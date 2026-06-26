#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TRACE_ID="${TRACE_ID:-demo-sbi-trace-001}"

echo "Health"
curl -s "$BASE_URL/health"
echo

echo "ICICI salary CREDIT INR 10000"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: $TRACE_ID" \
  -d '{
    "eventId": "evt-icici-salary-001",
    "accountId": "sbi-acct-123",
    "type": "CREDIT",
    "amount": 10000,
    "currency": "INR",
    "eventTimestamp": "2026-05-15T09:00:00Z",
    "metadata": { "source": "ICICI", "description": "Salary credit" }
  }'
echo

echo "Axis personal loan EMI DEBIT INR 2000"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: $TRACE_ID" \
  -d '{
    "eventId": "evt-axis-emi-001",
    "accountId": "sbi-acct-123",
    "type": "DEBIT",
    "amount": 2000,
    "currency": "INR",
    "eventTimestamp": "2026-05-15T10:00:00Z",
    "metadata": { "source": "Axis", "description": "Personal loan EMI" }
  }'
echo

echo "Duplicate salary event, balance should not change"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: $TRACE_ID" \
  -d '{
    "eventId": "evt-icici-salary-001",
    "accountId": "sbi-acct-123",
    "type": "CREDIT",
    "amount": 10000,
    "currency": "INR",
    "eventTimestamp": "2026-05-15T09:00:00Z",
    "metadata": { "source": "ICICI", "description": "Salary credit duplicate" }
  }'
echo

echo "Events sorted by original business timestamp"
curl -s "$BASE_URL/events?account=sbi-acct-123"
echo

echo "Current balance, expected INR 8000"
curl -s "$BASE_URL/accounts/sbi-acct-123/balance"
echo
