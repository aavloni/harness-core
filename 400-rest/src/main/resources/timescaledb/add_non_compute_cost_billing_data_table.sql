-- Copyright 2020 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Shield 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

BEGIN;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS NETWORKCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS NETWORKCOST DOUBLE PRECISION;
ALTER TABLE BILLING_DATA ADD COLUMN IF NOT EXISTS PRICINGSOURCE TEXT;
ALTER TABLE BILLING_DATA_HOURLY ADD COLUMN IF NOT EXISTS PRICINGSOURCE TEXT;
COMMIT;
