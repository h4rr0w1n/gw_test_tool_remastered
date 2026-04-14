# AMHS/SWIM Gateway Manual Verification Guide

This document provides detailed manual verification steps for each test case (CTSW101-CTSW116) as required by ICAO EUR Doc 047. Since the tool serves as a **Manual Payload Injector**, testers must verify the results at the target system (AMHS User Terminal or Gateway Control Position).

---

## Domain G: Normal Message Conversion

| ID | Title | Purpose | Manual Verification Steps |
|:---|:---|:---|:---|
| **CTSW101** | Unaware AMQP | Verify conversion of basic AMQP messages. | 1. Confirm arrival of message at AMHS terminal.<br>2. Check Priority (should be DD/Normal).<br>3. Verify payload text matches injection. |
| **CTSW103** | Service Level | Verify mapping of `amhs_service_level`. | 1. Confirm arrival of 6 messages.<br>2. Check P1/P2 headers for Service Level attributes (Basic, Extended, Content-Based, Recipient-Based). |
| **CTSW104** | Priority Mapping| Verify numeric (0-9) and explicit (SS-KK) mappings. | 1. Confirm 15 messages arrive.<br>2. Cross-check Priority field of each message against EUR Doc 047 Table 5-1.<br>3. Verify explicit SS/DD/FF overrides work. |
| **CTSW105** | Filing Time | Verify `amhs_filing_time` mapping. | 1. Msg 1 (No FT): Filing Time should be current GMT.<br>2. Msg 2 (Explicit FT): Filing Time must be '250102120000'. |
| **CTSW106** | OHI Mapping | Verify Originator-Handled-Identifier truncation. | 1. High Priority (SS/DD/FF): OHI truncated at 48 chars.<br>2. Low Priority (GG/KK): OHI truncated at 53 chars. |
| **CTSW107** | Subject Mapping | Verify `subject` mapping and truncation. | 1. Long Subject: Truncated at 128 chars.<br>2. Verify 'amhs_subject' property mapping as well. |
| **CTSW108** | Known Originator| Verify O/R address mapping for known IDs. | 1. Confirm P1 Originator field matches mapping for 'VVTSYMYX'. |
| **CTSW109** | Unknown Originator| Verify handling of invalid O/R addresses. | 1. Check if message is rejected or uses a default fallback address. |

---

## Domain H: Rejection and Validation (*** Cases)

| ID | Title | Purpose | Manual Verification Steps |
|:---|:---|:---|:---|
| **CTSW102** | Missing Info | Rejection for missing recipients/priority. | 1. Confirm NO messages arrive at AMHS terminal.<br>2. Check Gateway log for 'Invalid Priority' or 'Missing Destination'. |
| **CTSW110** | Content-Type | Rejection for unsupported content-type combinations. | 1. ONLY Msg 3 (Binary Data) and Msg 4 (Text AmqpValue) should arrive.<br>2. Verify rejection of Empty Body and UTF-16 messages. |
| **CTSW111** | Size Limit | Rejection when payload size > `gateway.max_size`.| 1. Confirm FIRST TWO (within limit) arrive.<br>2. Confirm LAST TWO (over limit) are rejected. |
| **CTSW112** | Recipient Limit | Rejection when recipient count > limit (e.g. 512). | 1. Confirm Gateway rejection.<br>2. Verify no delivery in AMHS backend logs. |

---

## Domain I: Body Part Type and Encoding

| ID | Title | Purpose | Manual Verification Steps |
|:---|:---|:---|:---|
| **CTSW115** | Encoding Mapping | Mapping to GeneralText/IA5Text body parts. | 1. IA5: Should be ia5-text-body-part.<br>2. ISO-646: Should be general-text-body-part.<br>3. Verify encoding identifiers in O/R structure. |
| **CTSW116** | FTBP & GZIP | FTBP attributes and GZIP decompression. | 1. Msg 1 (FTBP): Check filename 'test.txt' and size 1024.<br>2. Msg 2 (GZIP): Confirm plain text content matches injection. |

---

## Domain J: Incoming Reports and Notifications

| ID | Title | Purpose | Manual Verification Steps |
|:---|:---|:---|:---|
| **CTSW113** | RN/NRN | Handling of Receipt Notification requests. | 1. Confirm Priority is SS (mapped from AMQP 6).<br>2. Manually RETURN an NRN for Msg 1 and RN for Msg 2.<br>3. Confirm reports arrive on SWIM side. |
| **CTSW114** | NDR Handling | Forwarding of AMHS NDRs to SWIM side. | 1. Manually DELETE Msg 1 at AMHS terminal.<br>2. Confirm an AMQP NDR message is produced on the 'REPORTS' topic. |
