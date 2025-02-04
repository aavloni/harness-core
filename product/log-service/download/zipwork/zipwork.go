// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package zipwork

import (
	"archive/zip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"time"

	"golang.org/x/sync/errgroup"

	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/entity"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/queue"
	"github.com/harness/harness-core/product/log-service/store"
)

const usePrefixParam = "prefix"

func Work(ctx context.Context, wID string, q queue.Queue, c cache.Cache, s store.Store, cfg config.Config) {
	logEntry := logger.FromContext(ctx).
		WithField("stream name", cfg.ConsumerWorker.StreamName).
		WithField("consumer group", cfg.ConsumerWorker.ConsumerGroup)

	// start each goroutine per consumer download
	logEntryWorker := logEntry.WithField("consumer", wID)

	logEntryWorker.
		WithField("time", time.Now().Format(time.RFC3339)).
		Infoln("Consumer routine ", wID, " started")

	for {
		logEntryWorker.
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("consumer-initiated listening")

		message, err := q.Consume(ctx, cfg.ConsumerWorker.StreamName, cfg.ConsumerWorker.ConsumerGroup, wID)
		if err != nil {
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithError(err).
				Errorln("consumer execute: cannot process message")
			continue
		}

		var event entity.EventQueue
		b := message[usePrefixParam].(string)
		err = json.Unmarshal([]byte(b), &event)
		if err != nil {
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithError(err).
				Errorln("consumer execute: cannot unmarshal message")
			continue
		}

		logEntryWorker.
			WithField("files", event.FilesInPage).
			Infoln("Starting download IN_PROGRESS for prefix:", event.Key)

		var info entity.ResponsePrefixDownload
		infoBytes, err := c.Get(ctx, event.Key)
		if err != nil {
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithField("key", event.Key).
				WithError(err).
				Errorln("consumer execute: cannot get info from cache")
			continue
		}

		err = json.Unmarshal(infoBytes, &info)
		if err != nil {
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithField("key", event.Key).
				WithError(err).
				Errorln("consumer execute: cannot unmarshal info from cache")
			continue
		}

		info.Status = entity.IN_PROGRESS
		err = c.Create(context.Background(), event.Key, info, cfg.CacheTTL)
		if err != nil {
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithField("key", event.Key).
				WithError(err).
				Errorln("consumer execute: cannot create update cache info")
			continue
		}

		logEntryWorker.
			WithField("time", time.Now().Format(time.RFC3339)).
			WithField("key", event.Key).
			Infoln("message has been processed to IN_PROGRESS by ", wID)

		internal, cancel := context.WithCancel(context.Background())

		err = downloadZipUploadRoutine(internal, s, event.ZipKey, event.FilesInPage)
		if err != nil {
			cancel()
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithField("Error While downloadZipUploadRoutine for key:", event.Key).
				Errorln(err.Error())

			info.Status = entity.ERROR
			info.Message = err.Error()

			err = c.Create(context.Background(), event.Key, info, cfg.CacheTTL)
			if err != nil {
				logEntryWorker.
					WithField("time", time.Now().Format(time.RFC3339)).
					WithField("key", event.Key).
					WithError(err).
					Errorln("consumer execute: cannot create update cache info")
				continue
			}
		}

		info.Status = entity.SUCCESS
		err = c.Create(context.Background(), event.Key, info, cfg.CacheTTL)
		if err != nil {
			logEntryWorker.
				WithField("time", time.Now().Format(time.RFC3339)).
				WithField("key", event.Key).
				WithError(err).
				Errorln("consumer execute: cannot create update cache info")
			continue
		}
		logEntryWorker.WithField("key", event.Key).Infoln("cache marked as SUCCESS")
	}
}

func downloadZipUploadRoutine(ctx context.Context, s store.Store, zipPrefix string, out []string) error {
	pipeRead, pipeWrite := io.Pipe()
	zipWriter := zip.NewWriter(pipeWrite)

	gErrGroup := new(errgroup.Group)

	// upload
	gErrGroup.Go(func() error {
		err := s.Upload(ctx, zipPrefix, pipeRead)

		if err != nil {
			return fmt.Errorf("zip upload: cannot upload zip to s3: %w", err)
		}
		return nil
	})

	// zip
	gErrGroup.Go(func() error {
		for _, key := range out {
			// skip download logs.zip in the new zip
			if strings.Contains(key, "logs.zip") {
				continue
			}

			fileDownloaded, err := s.Download(ctx, key)
			if err != nil {
				return fmt.Errorf("zipfile: failed to download: %w", err)
			}

			zipFile, err := zipWriter.Create(key)
			if err != nil {
				return fmt.Errorf("zipfile: failed to zip: %w", err)
			}
			_, err = io.Copy(zipFile, fileDownloaded)
			if err != nil {
				return fmt.Errorf("zipfile: failed to copy: %w", err)
			}
			err = fileDownloaded.Close()
			if err != nil {
				return fmt.Errorf("zipfile: failed to close: %w", err)
			}
		}
		zipWriter.Close()
		pipeWrite.Close()
		return nil
	})

	err := gErrGroup.Wait()
	if err != nil {
		return err
	}

	return nil
}
