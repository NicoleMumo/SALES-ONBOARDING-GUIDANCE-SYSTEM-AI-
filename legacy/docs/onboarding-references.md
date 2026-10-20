# Onboarding References

## Purpose

Collect reliable references that describe real onboarding requirements, fields, verification activities, and processes for the industries supported by the prototype.

These references will support the creation of realistic onboarding sessions and training data later.

The references do not define one fixed onboarding sequence for every customer. They provide evidence about the fields, verification activities, and requirements that may occur during onboarding.

## 1. Banking

### Reference Source 1 — Central Bank of Kenya Customer Due Diligence Guidance

**Source:** Central Bank of Kenya, Guidance on Customer Due Diligence (CDD), 2025.

**Source type:** Official regulatory guidance.

**What it provides:**

The guidance applies to financial institutions including commercial banks, mortgage finance companies, microfinance banks, money remittance providers, foreign exchange bureaus, payment service providers, and non-deposit taking credit providers.

It provides requirements related to customer due diligence, including customer identification, verification, beneficial-owner information where applicable, and understanding the purpose and intended nature of the business relationship.

**Use in the project:**

This source provides reliable reference information for identifying banking onboarding fields and verification activities that may appear in onboarding sessions.

### Reference Source 2 — Banking Customer Due Diligence Requirements

**Source:** Central Bank of Kenya, Guidance on Customer Due Diligence (CDD), 2025.

**Reference onboarding activities:**

1. Identify the customer.
2. Verify the customer's identity.
3. Identify and verify the beneficial owner where applicable.
4. Obtain information about the purpose and intended nature of the business relationship.
5. Continue customer due diligence based on the customer's relationship and risk profile.

**Use in the project:**

These activities provide reference patterns that can be represented as onboarding events when creating realistic banking onboarding sessions.

### Banking Reference Fields and Activities

Examples of information or activities that may appear in banking onboarding include:

- Customer identification
- Identity verification
- Beneficial-owner information where applicable
- Purpose of the business relationship
- Intended nature of the business relationship
- Customer risk information
- Additional due-diligence activities where required

These are reference fields and activities, not a fixed sequence that every banking customer must follow.

## 2. Telecommunications

### Reference Source 1 — Kenya Information and Communications (Registration of Telecommunications Service Subscribers) Regulations, 2025

**Source:** Kenya Law, Legal Notice 90 of 2025.

**Source type:** Official Kenyan regulation.

**What it provides:**

The regulations define the requirements and process for registering telecommunications service subscribers.

They require the telecommunications operator or registration agent to obtain original identification documents before registration and to verify the information provided.

The regulations also define the registration process, including entering registration particulars, verifying documents and identification information, updating information where necessary, and maintaining registration records.

**Use in the project:**

This source provides reliable reference information for telecommunications onboarding fields, verification activities, and registration events.

### Reference Onboarding Fields

The regulations identify registration information and identification documents that may be required.

Examples include:

- Subscriber identification particulars
- Full name
- Identification document information
- Date of birth
- Gender
- Physical address
- Postal address where applicable
- Subscriber number
- Identification documents
- Additional information required for particular subscriber types

### Reference Onboarding Activities

1. Collect the subscriber's registration information.
2. Collect the required identification documents.
3. Verify the provided information.
4. Enter the verified registration information.
5. Update information where necessary.
6. Maintain the registration record.
7. Complete subscriber registration.

These activities represent a simplified reference process. They do not represent the exact sequence used by every telecommunications operator or every subscriber.

### Additional Reference — Subscriber Types

The 2025 regulations identify different documentation requirements for different subscriber situations.

Examples include:

- Kenyan citizens
- Children
- Foreign nationals
- Refugees
- Stateless persons
- Companies

This demonstrates that the information and documents required during onboarding can depend on the characteristics of the subscriber.

## 3. Relevance to the Onboarding Event Structure

The references above provide real-world examples of onboarding information, verification activities, and requirements.

These can be represented using the project's onboarding event structure.

For example:

```text
Industry: Telecommunications
Session ID: session_001
Field: identification document
Action: entered
Time spent: recorded
Error status: false
Revisited: false
Completion status: incomplete