# Synthetic Dataset Schema

## Overview

This document defines the structure of the synthetic datasets required for the **Sales Agent Onboarding Guidance System**.

The dataset represents **Customer Onboarding** sessions and the behaviour of the **Sales Agent** during onboarding. The current generated CSV can contain both banking and telecom sessions; the `industry` column identifies each workflow. Their field sequences remain industry-specific.

The synthetic datasets will support the Transformer-based component of the system in predicting the **Next Onboarding Field** and support **Real-Time Guidance**, **field validation**, and **Onboarding Risk** assessment.

---

## Current generated dataset and reproducibility

The sole generator is the standalone Jupyter/Colab notebook at
`notebooks/onboarding_dataset_generator.ipynb`. Run it from top to bottom,
choose the session count, industry, seed, and output path in its first code
cell, then run the generation and validation cells. It uses only the Python
standard library and runs locally or in Google Colab. In Colab, the final
optional cell downloads the generated CSV.

The same seed and notebook version produce the same rows, including IDs,
timestamps, event randomness, and split assignments. Synthetic timestamps
begin on 1 January 2026 and advance 30 minutes per generated session; they are
not real customer activity times.

Every row includes two independent split columns:

| Column | Meaning |
|---|---|
| `split` | Session-level train/validation/test split. All events for a session stay together; assignment is stratified by industry and scenario. This is the split used by the existing training notebook. |
| `agent_split` | Agent-level train/validation/test split. All sessions assigned to the same `sales_agent_id` stay together, enabling a stricter evaluation where agents do not cross splits. |

Both use 70% train, 15% validation, and 15% test by group, subject to
rounding. These columns support separate evaluation protocols; do not combine
them or claim an agent-level score when using `split`.

## Dataset Format

The datasets will use a **tabular CSV format**.

Each row represents one **Onboarding Event** that occurs during an **Onboarding Session**. Multiple rows can belong to the same `session_id`, allowing the sequence of events performed during a session to be reconstructed.

---

# 1. Banking Onboarding Dataset

## Reference Source Dataset (not used to generate the current CSV)

**Fintech Onboarding Event Log**

- Type: Real dataset
- Rows: **537,094**
- Columns: **6**

This real dataset is a reference for understanding financial onboarding
events. The notebook does not load or merge those rows;
`data/onboarding_dataset.csv` is synthetically generated.

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

## Reference Source Datasets (not used to generate the current CSV)

### Telecom Customer Onboarding Timeline

- Type: Synthetic dataset
- Rows: **200**
- Columns: **21**

This synthetic timeline is a reference for telecom onboarding. It is not
loaded by the current generator.

### Telco Customer Churn

- Type: Real dataset
- Rows: **7,043**
- Columns: **21**

This real churn dataset is a reference for customer and service context. It
is not loaded by the current generator.

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

## Completion-risk feature rules

The Android completion-risk calculator uses the same four explainable signals
as the synthetic-session generator:

- Start with a baseline score of `0.05`.
- Add `0.25` when one or more validation errors occur.
- Add `0.15` when elapsed session time is greater than 15 minutes.
- Add `0.10` for each field revisit.
- Add `0.10` when progress is below 50 percent.

The score is capped at `1.00`. Scores below `0.30` are `Low`, scores from
`0.30` up to (but not including) `0.70` are `Medium`, and scores of `0.70`
or higher are `High`. Each triggered signal is returned as an explanation so
future guidance screens can tell the Sales Agent why a session is considered
difficult or at risk of being incomplete.

`CompletionRiskScorer` derives these features from onboarding events by summing
event durations, counting failed validations and revisits, and using the latest
event's progress percentage. An empty event list starts at zero progress, so
it receives the low-progress signal while the session has not started.

`CompletionRiskGuidanceMapper` adds assistance when the score is `Medium` or
`High`. Medium risk asks the agent to offer extra help and review the
information. High risk asks the agent to pause and help complete the remaining
information. Low-risk sessions keep the normal step-by-step guidance.

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

The generated CSV may contain both industries in one file. Industry-specific
field definitions and session sequences are still selected separately by the
generator; consumers should filter on `industry` when they need an
industry-specific view.

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