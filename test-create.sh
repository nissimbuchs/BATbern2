#!/bin/bash
TOKEN=$(jq -r '.accessToken' ~/.batbern/staging.json)
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "name": "Test Company",
    "displayName": "Test Co",
    "industry": "Technology",
    "website": "https://test.example.com",
    "description": "Test company"
  }' \
  'https://bitta16ufc.execute-api.eu-central-1.amazonaws.com/v1/api/v1/companies'
