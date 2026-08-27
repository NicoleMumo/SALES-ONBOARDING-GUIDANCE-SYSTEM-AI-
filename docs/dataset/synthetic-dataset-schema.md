# Synthetic Dataset Schema

## Overview

This document defines the structure of the synthetic datasets required for the **Sales Agent Onboarding Guidance System**.

The datasets represent **Customer Onboarding** sessions and the behaviour of the **Sales Agent** during onboarding. Banking and telecom data are kept **separate** because the two industries have different onboarding processes, fields, and events.

The synthetic datasets will support the Transformer-based component of the system in predicting the **Next Onboarding Field** and support **Real-Time Guidance**, **field validation**, and **Onboarding Risk** assessment.

---

## Dataset Format

The datasets will use a **tabular CSV format**.

Each row represents one **Onboarding Event** that occurs during an **Onboarding Session**. Multiple rows can belong to the same `session_id`, allowing the sequence of events performed during a session to be reconstructed.

---

# 1. Banking Onboarding Dataset

## Source Dataset

**Fintech Onboarding Event Log**

- Type: Real dataset
- Rows: **537,094**
- Columns: **6**

The dataset provides event-level information from a financial onboarding process and is useful for understanding the sequence and timing of onboarding events.

### Relevant Source Columns

| Source Column | Data Type | Use |
|---|---|---|
| `concept:name` | String | Used to identify the type of onboarding event. |
| `time:timestamp` | DateTime | Used to preserve the order and timing of events. |
| `concept:status` | String | Used to represent the status of an onboarding event. |
| `case:concept:name` | Integer | Used to identify an onboarding case/session. |
| `concept:device` | String | Used where device information is relevant to the onboarding event. |

The `concept:fraud` column is **not included** in the synthetic system schema because fraud detection and prevention are outside the scope of the project.

## Banking Synthetic Dataset Schema

| Column | Data Type | Description |
|---|---|---|
| `session_id` | String | Unique identifier for a Banking Onboarding Session. |
| `customer_id` | String | Unique identifier for the banking Customer. |
| `sales_agent_id` | String | Identifier for the Sales Agent handling the session. |
| `onboarding_field` | String | Banking Onboarding Field currently being completed. |
| `event_name` | String | Banking Onboarding Event performed during the session. |
| `event_timestamp` | DateTime | Date and time at which the Onboarding Event occurred. |
| `event_status` | String | Status of the current Onboarding Event. |
| `event_duration_minutes` | Float | Time taken to complete the current Onboarding Event. |
| `previous_onboarding_field` | String | Onboarding Field completed immediately before the current field. |
| `next_onboarding_field` | String | Next Banking Onboarding Field to be predicted by the Transformer. |
| `validation_status` | String | Indicates whether the information entered passed field validation. |
| `failure_reason` | String | Reason for an unsuccessful onboarding event, where applicable. |
| `onboarding_progress` | Float | Percentage of the Customer Onboarding process completed at the time of the event. |
| `onboarding_risk_score` | Float | Numerical score representing the risk that the Customer Onboarding may not be completed successfully. |
| `onboarding_risk_level` | String | Category representing the Onboarding Risk, such as Low, Medium, or High. |
| `guidance_action` | String | Real-Time Guidance presented to the Sales Agent based on the current onboarding situation. |
| `session_status` | String | Overall status of the Banking Onboarding Session. |

---

# 2. Telecom Onboarding Dataset

## Source Datasets

### Telecom Customer Onboarding Timeline

- Type: Synthetic dataset
- Rows: **200**
- Columns: **21**

This dataset provides telecom-specific onboarding information, including onboarding steps, status, timing, channel, agent involvement, failure reasons, and risk information.

### Telco Customer Churn

- Type: Real dataset
- Rows: **7,043**
- Columns: **21**

This dataset provides telecom customer and service information that can be used to represent the telecom customer context.

## Telecom Synthetic Dataset Schema

| Column | Data Type | Description |
|---|---|---|
| `session_id` | String | Unique identifier for a Telecom Onboarding Session. |
| `customer_id` | String | Unique identifier for the telecom Customer. |
| `sales_agent_id` | String | Identifier for the Sales Agent handling the session. |
| `service_type` | String | Telecom service associated with the Customer Onboarding. |
| `onboarding_field` | String | Telecom Onboarding Field currently being completed. |
| `event_name` | String | Telecom Onboarding Event performed during the session. |
| `event_timestamp` | DateTime | Date and time at which the Onboarding Event occurred. |
| `event_status` | String | Status of the current Onboarding Event. |
| `event_duration_minutes` | Float | Time taken to complete the current Onboarding Event. |
| `onboarding_channel` | String | Channel used for the Customer Onboarding. |
| `previous_onboarding_field` | String | Onboarding Field completed immediately before the current field. |
| `next_onboarding_field` | String | Next Telecom Onboarding Field to be predicted by the Transformer. |
| `validation_status` | String | Indicates whether the information entered passed field validation. |
| `failure_reason` | String | Reason for an unsuccessful onboarding event, where applicable. |
| `onboarding_progress` | Float | Percentage of the Customer Onboarding process completed at the time of the event. |
| `onboarding_risk_score` | Float | Numerical score representing the risk that the Customer Onboarding may not be completed successfully. |
| `onboarding_risk_level` | String | Category representing the Onboarding Risk, such as Low, Medium, or High. |
| `guidance_action` | String | Real-Time Guidance presented to the Sales Agent based on the current onboarding situation. |
| `session_status` | String | Overall status of the Telecom Onboarding Session. |

---

# Transformer Input and Output

The Transformer learns from the sequence of Onboarding Events that have already occurred during an Onboarding Session.

## Transformer Input

The model can use information such as:

- Previous Onboarding Events
- Current Onboarding Field
- Event Status
- Event Timestamp
- Validation Status
- Onboarding Progress
- Other relevant session information

## Transformer Output

The main prediction is:

**Next Onboarding Field**

The `next_onboarding_field` column is therefore the prediction target and should not be provided to the model as an input feature during training.

---

# Real-Time Guidance

The predicted **Next Onboarding Field** is used to provide **Real-Time Guidance** to the Sales Agent.

For example:

**Next Onboarding Field:** Customer Address

**Real-Time Guidance:** Complete the Customer Address field next.

The Sales Agent remains responsible for performing the recommended action. The system provides guidance and does not automatically complete the onboarding field.

---

# Field Validation

The system provides **real-time field validation** during Customer Onboarding.

The `validation_status` field records whether information entered by the Sales Agent satisfies the required validation rules.

Where an onboarding event cannot be completed successfully, the `failure_reason` field records the reason where applicable.

---

# Onboarding Risk

The system provides **Onboarding Risk** information to help identify Customer Onboarding sessions that may not be completed successfully.

The risk is represented using:

- `onboarding_risk_score`
- `onboarding_risk_level`

The risk information can be displayed to the Sales Agent together with the Real-Time Guidance.

---

# Industry Separation

The Banking and Telecom datasets remain separate throughout dataset preparation and model development.

## Banking

The Banking dataset uses banking-specific:

- Onboarding Events
- Onboarding Fields
- Validation processes
- Customer information
- Onboarding Risk information

## Telecom

The Telecom dataset uses telecom-specific:

- Onboarding Events
- Onboarding Fields
- Service information
- Installation and activation processes
- Validation processes
- Onboarding Risk information

The datasets are not merged into a single cross-industry dataset.

---

# Out-of-Scope Data

The following information is not included unless it directly supports a declared system function:

- Fraud detection and prevention
- Commission tracking
- Payment processing
- Performance analytics
- CRM information
- Unrelated customer analytics

This ensures that the synthetic dataset remains focused on the declared functions of the **Sales Agent Onboarding Guidance System**.

---

# Summary

The synthetic dataset schemas provide the structure required to represent:

**Customer → Onboarding Session → Onboarding Events → Sales Agent Behaviour → Next Onboarding Field → Field Validation → Onboarding Risk → Real-Time Guidance**

The Banking and Telecom datasets remain separate while following the same overall system concept. This preserves the specific characteristics of each industry's Customer Onboarding process while supporting the development of the Transformer-based guidance system.