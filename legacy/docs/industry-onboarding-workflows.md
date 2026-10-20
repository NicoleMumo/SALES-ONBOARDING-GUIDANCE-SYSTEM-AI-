# Industry Onboarding Workflows

## Purpose

Define realistic onboarding workflows for the supported industries using the researched onboarding references.

The workflows identify:

- The onboarding fields that must be provided.
- The checks that must be performed.
- The order in which onboarding steps normally occur.
- Situations where the onboarding sequence may change.

These workflows are reference workflows. They are not the final Transformer training data.

The workflows provide a structured basis for creating onboarding sessions and onboarding events for the system.

## Relationship to the System

The system represents onboarding as a sequence of onboarding events.

During an onboarding session, the sales agent enters information for the customer. Each field entry, correction, verification, or other onboarding action can produce an onboarding event.

The Transformer will use the sequence of onboarding events from the current onboarding session to predict the next onboarding field.

The guidance engine will use the Transformer's prediction to provide guidance to the sales agent.

Therefore, these industry workflows define the expected onboarding context that will be used when designing and generating onboarding event sequences.

---

## 1. Banking Onboarding Workflow

### 1.1 Reference Basis

The banking workflow is based on the customer due diligence requirements identified in the Central Bank of Kenya's customer due diligence guidance.

The reference process includes customer identification, identity verification, beneficial-owner information where applicable, information about the purpose and intended nature of the business relationship, and continued due diligence based on the customer's relationship and risk profile.

This workflow is a simplified reference workflow. It does not represent the exact onboarding workflow of every bank.

### 1.2 Onboarding Fields

The banking onboarding session may include the following fields:

| Field | Purpose |
|---|---|
| Customer Name | Identifies the customer |
| Identification Document Type | Identifies the type of identification document provided |
| Identification Document Number | Records the customer's identification document number |
| Customer Date of Birth | Records the customer's date of birth where required |
| Customer Address | Records the customer's address |
| Beneficial Owner Information | Records beneficial-owner information where applicable |
| Purpose of Relationship | Records the purpose of the business relationship |
| Intended Nature of Relationship | Records the intended nature of the business relationship |
| Risk Information | Records information required for customer risk assessment |

The exact fields may vary depending on the customer and the banking service being provided.

### 1.3 Standard Workflow

The reference banking workflow is:

```text
Start
  ↓
Customer Identification
  ↓
Collect Identification Information
  ↓
Identity Verification
  ↓
Beneficial Owner Information
  ↓
Purpose of Relationship
  ↓
Intended Nature of Relationship
  ↓
Risk Assessment
  ↓
Complete Onboarding
```

### 1.4 Possible Sequence Variations

The onboarding sequence may change depending on the customer and the information collected during the onboarding session.

For example, beneficial-owner information may only be required where applicable.

A verification problem may also require the sales agent to correct or re-enter information before continuing.

Example:

```text
Customer Identification
  ↓
Identification Information
  ↓
Identity Verification
  ↓
Verification Fails
  ↓
Correct Identification Information
  ↓
Identity Verification
  ↓
Continue Onboarding
```

These variations demonstrate why an onboarding session may contain different sequences of onboarding events while still following the same overall onboarding workflow.

## 2. Telecommunications Onboarding Workflow

### 2.1 Reference Basis

The telecommunications workflow is based on the Kenya Information and Communications (Registration of Telecommunications Service Subscribers) Regulations, 2025.

The regulations require subscriber registration information and identification information.

This workflow is a simplified reference workflow. It does not represent the exact onboarding workflow of every telecommunications operator.

### 2.2 Onboarding Fields

The telecommunications onboarding session may include the following fields:

| Field | Purpose |
|---|---|
| Full Name | Identifies the subscriber |
| Identification Document Type | Identifies the type of identification document provided |
| Identification Document Number | Records the subscriber's identification document number |
| Date of Birth | Records the subscriber's date of birth |
| Gender | Records the subscriber's gender where required |
| Physical Address | Records the subscriber's physical address |
| Postal Address | Records the subscriber's postal address where applicable |
| Subscriber Information | Records other required subscriber information |

The exact fields may vary depending on the subscriber and the registration requirements.

### 2.3 Standard Workflow

The reference telecommunications workflow is:

```text
Start
  ↓
Collect Subscriber Information
  ↓
Collect Identification Information
  ↓
Verify Subscriber Information
  ↓
Enter or Update Verified Information
  ↓
Complete Subscriber Registration
```

### 2.4 Possible Sequence Variations

The onboarding sequence may change when information requires correction, verification fails, or additional information is required.

For example:

```text
Subscriber Information
  ↓
Identification Information
  ↓
Verify Subscriber Information
  ↓
Verification Fails
  ↓
Correct Subscriber Information
  ↓
Verify Subscriber Information
  ↓
Enter or Update Verified Information
  ↓
Complete Subscriber Registration
```

Another onboarding session may proceed without a correction:

```text
Subscriber Information
  ↓
Identification Information
  ↓
Verify Subscriber Information
  ↓
Enter or Update Verified Information
  ↓
Complete Subscriber Registration
```

These variations demonstrate that different onboarding sessions can produce different sequences of onboarding events while still following the same overall onboarding workflow.

## 3. Relationship Between Industry Workflows and Onboarding Events

The workflows above describe the expected onboarding process for each supported industry.

The actual onboarding session is represented as a sequence of onboarding events.

For example, a banking session may produce:

```text
Customer Name Entered
  ↓
Identification Document Number Entered
  ↓
Identity Verification Completed
  ↓
Verification Failed
  ↓
Identification Document Number Corrected
  ↓
Identity Verification Completed
```

A telecommunications session may produce:

```text
Full Name Entered
  ↓
Identification Document Number Entered
  ↓
Subscriber Information Verified
  ↓
Physical Address Entered
```

Each event provides information about what has happened during the current onboarding session.

The Transformer can use the sequence of previous onboarding events to predict the next onboarding field.

The guidance engine can then use that prediction to provide guidance to the sales agent.

The industry workflows therefore provide the reference structure for designing realistic onboarding event sequences.

They do not define a fixed list of events that every onboarding session must follow.

## 4. Summary

The supported industries have different onboarding requirements and reference workflows.

However, both industries can be represented using the same system concepts:

- Onboarding session
- Onboarding field
- Onboarding event
- Validation
- Verification
- Transformer
- Guidance engine

The onboarding workflow provides the reference process, while the onboarding events record what actually happens during each onboarding session.

This allows the system to represent different onboarding sequences while maintaining a consistent structure for processing onboarding events.