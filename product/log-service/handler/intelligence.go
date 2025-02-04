// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go/aws/awserr"
	"github.com/aws/aws-sdk-go/service/s3"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/store/bolt"
	"github.com/harness/harness-core/product/log-service/stream"
	"github.com/pkg/errors"
)

const (
	keysParam            = "keys"
	maxLogLineSize       = 500
	defaultBufSize       = 64 * 1024        // 64KiB
	maxBufSize           = 10 * 1024 * 1024 // 10MiB
	debugLogChars        = 200
	genAIPlainTextPrompt = `
Provide error message, root cause and remediation from the below logs preserving the markdown format.
Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. %s


Logs:
` + "```" + `
%s
%s
` + "```"

	genAIAzurePlainTextPrompt = `
Provide error message, root cause and remediation from the below logs preserving the markdown format.
Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. %s

Logs:
` + "```" + `
%s
%s
` + "```" + `

Provide your output in the following format:
` + "```" + `
## Error message
<Error message>

## Root cause
<Root cause>

## Remediation
<Remediation>
` + "```"

	genAIJSONPrompt = `
Provide error message, root cause and remediation from the below logs. Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. Return list of json object with three keys using the following format {"error", "cause", "remediation"}. %s

Logs:
` + "```" + `
%s
%s
` + "```"

	genAIBisonJSONPrompt = `
I have a set of logs. The logs contain error messages. I want you to find the error messages in the logs, and suggest root cause and remediation or fix suggestions. Remediation is required in the response - error message and root cause can be truncated if needed, but make sure to preserve the markdown format. I want you to give me the response in JSON format, no text before or after the JSON. Example of response:
[
	{
		"error": "error_1",
		"cause": "cause_1",
		"remediation": "fix line 2 of the command"
	},
	{
		"error": "error_2",
		"cause": "cause_2",
		"remediation": "fix line 5 of the command"
	}
]
%s

Here is the logs, remember to give the response only in json format like the example provided above, no text before or after the json object:
` + "```" + `
%s
%s
` + "```"

	genAITemperature = 0.0
	genAITopP        = 1.0
	genAITopK        = 1
	errSummaryParam  = "err_summary"
	infraParam       = "infra"
	stepTypeParam    = "step_type"
	commandParam     = "command"
	osParam          = "os"
	archParam        = "arch"
	pluginParam      = "plugin"

	azureAIProvider  = "azureopenai"
	azureAIModel     = "gpt3"
	vertexAIProvider = "vertexai"
	vertexAIModel    = "text-bison"
)

const (
	genAIResponseJSONFirstChar rune = '['
	genAIResponseJSONLastChar  rune = ']'
)

type (
	RCAReport struct {
		Rca     string      `json:"rca"`
		Results []RCAResult `json:"detailed_rca"`
	}

	RCAResult struct {
		Error       string `json:"error"`
		Cause       string `json:"cause"`
		Remediation string `json:"remediation"`
	}
)

func HandleRCA(store store.Store, cfg config.Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		keys, err := getKeys(r)
		if err != nil {
			WriteBadRequest(w, err)
			return
		}

		logger.FromRequest(r).WithField("keys", keys).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: rca call received, fetching logs")

		logs, err := fetchLogs(ctx, store, keys, cfg.GenAI.MaxInputPromptLen)
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("latency", time.Since(st)).
				WithField("keys", keys).
				Errorln("api: could not fetch logs for rca call")
			return
		}

		stepType := r.FormValue(stepTypeParam)
		command := r.FormValue(commandParam)
		errSummary := r.FormValue(errSummaryParam)

		logger.FromRequest(r).WithField("keys", keys).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: fetched logs for rca call, initiating call to ml service")

		genAISvcURL := cfg.GenAI.Endpoint
		genAISvcSecret := cfg.GenAI.ServiceSecret
		provider := cfg.GenAI.Provider
		maxOutputTokens := cfg.GenAI.MaxOutputTokens
		useJSONResponse := cfg.GenAI.UseJSONResponse
		report, prompt, err := retrieveLogRCA(ctx, genAISvcURL, genAISvcSecret,
			provider, logs, maxOutputTokens, useJSONResponse, r)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("latency", time.Since(st)).
				WithField("keys", keys).
				Errorln("api: failed to predict RCA")
			return
		}

		logPrompt := prompt

		// don't print full prompt if debug mode is disabled
		if !cfg.GenAI.Debug {
			logPrompt = trim(prompt, debugLogChars)
		}

		logger.FromRequest(r).
			WithField("keys", keys).
			WithField("latency", time.Since(st)).
			WithField("command", trim(command, debugLogChars)).
			WithField("step_type", stepType).
			WithField("logs", trim(logs, debugLogChars)).
			WithField("prompt", logPrompt).
			WithField("error_summary", errSummary).
			WithField("time", time.Now().Format(time.RFC3339)).
			WithField("response.rca", report.Rca).
			WithField("response.results", report.Results).
			Infoln("api: successfully retrieved RCA")
		WriteJSON(w, report, 200)
	}
}

func retrieveLogRCA(ctx context.Context, endpoint, secret, provider,
	logs string, maxOutputTokens int, useJSONResponse bool, r *http.Request) (
	*RCAReport, string, error) {
	promptTmpl := genAIPlainTextPrompt
	if useJSONResponse {
		promptTmpl = genAIJSONPrompt
		if provider == vertexAIProvider {
			promptTmpl = genAIBisonJSONPrompt
		}
	} else {
		if provider == azureAIProvider {
			promptTmpl = genAIAzurePlainTextPrompt
		}
	}

	prompt := generatePrompt(r, logs, promptTmpl)
	client := genAIClient{endpoint: endpoint, secret: secret}

	response, isBlocked, err := predict(ctx, client, provider, prompt, maxOutputTokens)
	if err != nil {
		return nil, prompt, err
	}
	if isBlocked {
		return nil, prompt, errors.New("received blocked response from genAI")
	}
	if useJSONResponse {
		report, err := parseGenAIResponse(response)
		return report, prompt, err
	}
	return &RCAReport{Rca: response}, prompt, nil
}

func predict(ctx context.Context, client genAIClient, provider, prompt string, maxOutputTokens int) (string, bool, error) {
	switch provider {
	case vertexAIProvider:
		response, err := client.Complete(ctx, vertexAIProvider, vertexAIModel, prompt,
			genAITemperature, genAITopP, genAITopK, maxOutputTokens)
		if err != nil {
			return "", false, err
		}
		return response.Text, response.Blocked, nil
	case azureAIProvider:
		response, err := client.Chat(ctx, azureAIProvider, azureAIModel, prompt,
			genAITemperature, -1, -1, maxOutputTokens)
		if err != nil {
			return "", false, err
		}
		return response.Text, response.Blocked, nil
	default:
		return "", false, fmt.Errorf("unsupported provider %s", provider)
	}
}

func generatePrompt(r *http.Request, logs, promptTempl string) string {
	stepType := r.FormValue(stepTypeParam)
	command := r.FormValue(commandParam)
	infra := r.FormValue(infraParam)
	errSummary := r.FormValue(errSummaryParam)
	os := r.FormValue(osParam)
	arch := r.FormValue(archParam)
	plugin := r.FormValue(pluginParam)

	platformCtx := ""
	if os != "" && arch != "" {
		platformCtx = fmt.Sprintf("%s %s ", os, arch)
	}
	stepCtx := ""
	if infra != "" {
		stepCtx += fmt.Sprintf("Logs are generated on %s%s %s.\n", platformCtx, infra, getStepTypeContext(stepType, infra))
	}
	if command != "" {
		stepCtx += fmt.Sprintf("Logs are generated by running command:\n```\n%s\n```", command)
	} else if plugin != "" {
		pluginType := ""
		if stepType == "Plugin" {
			pluginType = "drone plugin"
		} else if stepType == "Action" {
			pluginType = "github action"
		} else if stepType == "Bitrise" {
			pluginType = "bitrise plugin"
		}

		if pluginType != "" {
			stepCtx += fmt.Sprintf("The logs below were generated when running %s %s", pluginType, plugin)
		}
	}
	errSummaryCtx := ""
	if errSummary != "" && !matchKnownPattern(errSummary) {
		errSummaryCtx += errSummary
	}

	prompt := fmt.Sprintf(promptTempl, stepCtx, logs, errSummaryCtx)
	return prompt
}

func getStepTypeContext(stepType, infra string) string {
	switch stepType {
	case "liteEngineTask":
		if infra == "vm" {
			return "while initializing the virtual machine in Harness CI"
		}
		return "while creating a Pod in Kubernetes cluster for running Harness CI builds."
	case "BuildAndPushACR":
		return "while building and pushing the image to Azure Container Registry in Harness CI"
	case "BuildAndPushECR":
		return "while building and pushing the image to Elastic Container Registry in Harness CI"
	case "BuildAndPushGCR":
		return "while building and pushing the image to Google Container Registry in Harness CI"
	case "BuildAndPushDockerRegistry":
		return "while building and pushing the image to docker registry in Harness CI"
	case "GCSUpload":
		return "while uploading the files to GCS in Harness CI"
	case "S3Upload":
		return "while uploading the files to S3 in Harness CI"
	case "SaveCacheGCS":
		return "while saving the files to GCS in Harness CI"
	case "SaveCacheS3":
		return "while saving the files to S3 in Harness CI"
	case "RestoreCacheGCS":
		return "while restoring the files from GCS in Harness CI"
	case "RestoreCacheS3":
		return "while restoring the files from S3 in Harness CI"
	case "ArtifactoryUpload":
		return "while uploading the files to Jfrog artifactory in Harness CI"
	case "JiraUpdate":
		return "while updating the Jira ticket in Harness"
	case "K8sBlueGreenDeploy":
		return "while performing Blue Green Deployment for kubernetes in Harness CD"
	case "K8sRollingDeploy":
		return "while performing Rolling Deployment for Kubernetes in Harness CD"
	case "K8sRollingRollback":
		return "while performing Rolling Deployment rollback for Kubernetes in Harness CD"
	case "K8sApply":
		return "while applying the Kubernetes manifest in Harness CD"
	case "K8sScale":
		return "while scaling the Kubernetes deployment in Harness CD"
	case "K8sCanaryDeploy":
		return "while performing Canary Deployment for Kubernetes in Harness CD"
	case "K8sBGSwapServices":
		return "while swapping the Kubernetes services in Harness CD"
	case "K8sDelete":
		return "while deleting the Kubernetes deployment in Harness CD"
	case "K8sCanaryDelete":
		return "while deleting the Canary Deployment for Kubernetes in Harness CD"
	case "K8sDryRun":
		return "while performing a dry run of the Kubernetes manifest in Harness CD"
	case "ServerlessAwsLambdaDeploy":
		return "while deploying the AWS Lambda function in Harness CD"
	case "ServerlessAwsLambdaRollback":
		return "while rolling back the AWS Lambda function in Harness CD"
	case "ServerlessAwsLambdaPrepareRollbackV2":
		return "while preparing to rollback the AWS Lambda function in Harness CD"
	case "ServerlessAwsLambdaRollbackV2":
		return "while rolling back the AWS Lambda function in Harness CD"
	case "ServerlessAwsLambdaDeployV2":
		return "while deploying the AWS Lambda function in Harness CD"
	case "ServerlessAwsLambdaPackageV2":
		return "while packaging the AWS Lambda function in Harness CD"
	case "EcsRollingDeploy":
		return "while performing Rolling Deployment for ECS in Harness CD"
	case "EcsRollingRollback":
		return "while performing Rolling Deployment rollback for ECS in Harness CD"
	case "EcsCanaryDeploy":
		return "while performing Canary Deployment for ECS in Harness CD"
	case "EcsCanaryDelete":
		return "while deleting the Canary Deployment for ECS in Harness CD"
	case "EcsBlueGreenCreateService":
		return "while creating the ECS service for Blue Green Deployment in Harness CD"
	case "EcsBlueGreenRollback":
		return "while rolling back the ECS service for Blue Green Deployment in Harness CD"
	case "EcsRunTask":
		return "while running the ECS task in Harness CD"
	case "HelmDeploy":
		return "while deploying Helm chart into Kubernetes using Harness CD"
	case "HelmRollback":
		return "while rolling back Helm chart in Kubernetes using Harness CD"
	case "CanaryAppSetup":
		return "while setting up a canary app in Tanzu Application Service using Harness CD"
	case "BGAppSetup":
		return "while setting up a blue green deployment in Tanzu Application Service using Harness CD"
	case "BasicAppSetup":
		return "while setting up a basic app in Tanzu Application Service using Harness CD"
	case "AppResize":
		return "while resizing an app in Tanzu Application Service using Harness CD"
	case "SwapRoutes":
		return "while swapping routes in Tanzu Application Service using Harness CD"
	case "SwapRollback":
		return "while rolling back an app in Tanzu Application Service using Harness CD"
	case "TasRollingDeploy":
		return "while performing a rolling deployment in Tanzu Application Service using Harness CD"
	case "TasRollingRollback":
		return "while performing a rolling rollback in Tanzu Application Service using Harness CD"
	case "RouteMapping":
		return "while setting up route mapping in Tanzu Application Service using Harness CD"
	case "TerraformApply":
		return "while applying Terraform configuration to infrastructure using Harness CD"
	case "TerraformPlan":
		return "while planning Terraform configuration for infrastructure using Harness CD"
	case "TerraformDestroy":
		return "while destroying Terraform infrastructure using Harness CD"
	case "TerraformRollback":
		return "while rolling back Terraform changes to infrastructure using Harness CD"
	case "TerraformCloudRun":
		return "while running Terraform cloud using Harness CD"
	case "TerraformCloudRollback":
		return "while rolling back Terraform cloud changes using Harness CD"
	case "AwsCdkBootstrap":
		return "while bootstrapping AWS CDK environment using Harness CD"
	case "AwsCdkSynth":
		return "while synthesizing AWS CDK stack using Harness CD"
	case "AwsCdkDiff":
		return "while diffing AWS CDK stack using Harness CD"
	case "AwsCdkDeploy":
		return "while deploying AWS CDK stack using Harness CD"
	case "AwsCdkDestroy":
		return "while destroying AWS CDK stack using Harness CD"
	case "AwsSamDeploy":
		return "while deploying AWS SAM application using Harness CD"
	case "AwsSamBuild":
		return "while building AWS SAM application using Harness CD"
	case "AwsSamRollback":
		return "while rolling back AWS SAM application using Harness CD"
	}
	return ""
}

func fetchLogs(ctx context.Context, store store.Store, key []string, maxLen int) (
	string, error) {
	logs := ""
	for _, k := range key {
		l, err := fetchKeyLogs(ctx, store, k)
		if err != nil {
			return "", err
		}
		logs += l
	}

	// Calculate the starting position for retrieving the last N characters
	startPos := len(logs) - maxLen
	if startPos < 0 {
		startPos = 0
	}

	// Retrieve the last N characters from the buffer
	result := logs[startPos:]
	return result, nil
}

// fetchKeyLogs fetches the logs from the store for a given key
func fetchKeyLogs(ctx context.Context, store store.Store, key string) (
	string, error) {
	out, err := store.Download(ctx, key)
	if out != nil {
		defer out.Close()
	}
	if err != nil {
		// If the key does not exist, return empty string
		// This happens when logs are empty for a step
		if err == bolt.ErrNotFound {
			return "", nil
		}
		if aerr, ok := err.(awserr.Error); ok {
			if aerr.Code() == s3.ErrCodeNoSuchKey {
				return "", nil
			}
		}
		return "", err
	}

	var logs string

	scanner := bufio.NewScanner(out)
	buf := make([]byte, 0, defaultBufSize)
	scanner.Buffer(buf, maxBufSize)
	for scanner.Scan() {
		l := stream.Line{}
		if err := json.Unmarshal([]byte(scanner.Text()), &l); err != nil {
			return "", errors.Wrap(err, "failed to unmarshal log line")
		}

		logs += l.Message[:min(len(l.Message), maxLogLineSize)]
	}

	if err := scanner.Err(); err != nil {
		return "", err
	}
	return logs, nil
}

// parses the generative AI response into a RCAReport
func parseGenAIResponse(in string) (*RCAReport, error) {
	var rcaResults []RCAResult
	if err := json.Unmarshal([]byte(in), &rcaResults); err == nil {
		return &RCAReport{Results: rcaResults}, nil
	}

	// Response returned by the generative AI is not a valid json
	// Unmarshalled response is of type string. So, we need to unmarshal
	// it to string and then to []RCAReport
	var data interface{}
	if err := json.Unmarshal([]byte(in), &data); err != nil {
		return nil, errors.Wrap(err,
			fmt.Sprintf("response is not a valid json: %s", in))
	}
	switch value := data.(type) {
	case string:
		// Parse if response is a single RCA result
		var rcaResult RCAResult
		if err := json.Unmarshal([]byte(value), &rcaResult); err == nil {
			return &RCAReport{Results: []RCAResult{rcaResult}}, nil
		}

		v, err := jsonStringRetriever(value)
		if err != nil {
			return nil, err
		}
		var rcaResults []RCAResult
		if err := json.Unmarshal([]byte(v), &rcaResults); err != nil {
			return nil, errors.Wrap(err,
				fmt.Sprintf("response is not a valid json: %s", in))
		}
		return &RCAReport{Results: rcaResults}, nil
	case []RCAResult:
		return &RCAReport{Results: value}, nil
	default:
		return nil, fmt.Errorf("response is not a valid json: %v", value)
	}
}

// retrieves the JSON part of the generative AI response
// and trims the extra characters
func jsonStringRetriever(s string) (string, error) {
	firstIdx := strings.IndexRune(s, genAIResponseJSONFirstChar)
	if firstIdx == -1 {
		return "", fmt.Errorf("cannot find first character %c in %s", genAIResponseJSONFirstChar, s)
	}

	lastIndex := -1
	for i := len(s) - 1; i >= 0; i-- {
		if rune(s[i]) == genAIResponseJSONLastChar {
			lastIndex = i
			break
		}
	}
	if lastIndex == -1 {
		return "", fmt.Errorf("cannot find last character %c in %s", genAIResponseJSONLastChar, s)
	}

	return s[firstIdx : lastIndex+1], nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// matchKnownPattern checks if the error summary matches any of the known errors which do not
// add value to logs for RCA
func matchKnownPattern(s string) bool {
	if m, err := regexp.MatchString("exit status \\d+", s); err == nil && m {
		return true
	}
	if m, err := regexp.MatchString("1 error occurred: \\* exit status \\d+", s); err == nil && m {
		return true
	}
	if m, err := regexp.MatchString("Shell Script execution failed\\. Please check execution logs\\.", s); err == nil && m {
		return true
	}
	return false
}

func getKeys(r *http.Request) ([]string, error) {
	accountID := r.FormValue(accountIDParam)
	if accountID == "" {
		return nil, errors.New("accountID is required")
	}

	keysStr := r.FormValue(keysParam)
	if keysStr == "" {
		return nil, errors.New("keys field is required")
	}

	keys := make([]string, 0)
	for _, v := range strings.Split(keysStr, ",") {
		keys = append(keys, CreateAccountSeparatedKey(accountID, v))
	}
	return keys, nil
}

// given a string s, print the first n and the last n characters
func trim(s string, n int) string {
	length := len(s)
	if length <= 2*n {
		return s
	} else {
		return s[:n] + "..." + s[length-n:]
	}
}
