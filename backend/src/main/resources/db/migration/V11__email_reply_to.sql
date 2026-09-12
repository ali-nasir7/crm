-- V11: Email deliverability + no-reply reminder support.
-- reply_to: proper Reply-To header on outgoing email (deliverability practice, brief #6).
-- No new tables: the 3-day no-reply reminder reuses tasks (task_type REMINDER, OPEN/COMPLETED
-- gives natural idempotency) and email_messages records the send.
ALTER TABLE email_accounts ADD COLUMN reply_to VARCHAR(255);
