# Common Field Vocabulary

## Purpose

Define a standardized field vocabulary across the supported industries.

The vocabulary combines the onboarding fields identified in the banking and
telecommunications onboarding requirements.

Each standardized field is assigned a unique numerical ID.

The field IDs provide a consistent representation of onboarding fields that
can later be used when creating onboarding event sequences and Transformer
training data.

The vocabulary is a shared system definition. It is not the training dataset
itself.

## Relationship to the System

The supported industries use different onboarding requirements, but some
fields represent the same type of information.

For example, banking and telecommunications onboarding may both require
identification document information.

The common vocabulary represents these fields using one standardized field
name and one unique field ID.

This allows onboarding events from different industries to use the same field
representation.

The Transformer will later use these field IDs when processing onboarding
event sequences and predicting the next onboarding field.

The actual Transformer implementation and training will be performed
separately using the prepared dataset.

---

## 1. Standardized Field Vocabulary

| Field ID | Standardized Field | Description |
|---:|---|---|
| 0 | `customer_name` | Name used to identify the customer or subscriber. |
| 1 | `identification_document_type` | Type of identification document provided. |
| 2 | `identification_document_number` | Identification document number provided by the customer or subscriber. |
| 3 | `date_of_birth` | Customer or subscriber date of birth, where applicable. |
| 4 | `gender` | Customer or subscriber gender, where applicable. |
| 5 | `physical_address` | Customer or subscriber physical address. |
| 6 | `postal_address` | Customer or subscriber postal address, where applicable. |
| 7 | `subscriber_number` | Telephone or service number associated with subscriber registration. |
| 8 | `beneficial_owner_information` | Information identifying the beneficial owner where applicable. |
| 9 | `business_relationship_purpose` | Information describing the purpose of the business relationship. |
| 10 | `business_relationship_nature` | Information describing the intended nature of the business relationship. |
| 11 | `ownership_and_control_information` | Information about ownership and control structures where applicable. |
| 12 | `risk_information` | Information used for customer risk assessment where applicable. |

---

## 2. Industry Mapping

### Banking

The following standardized fields are supported by the banking onboarding
requirements:

| Field ID | Standardized Field |
|---:|---|
| 0 | `customer_name` |
| 1 | `identification_document_type` |
| 2 | `identification_document_number` |
| 3 | `date_of_birth` |
| 5 | `physical_address` |
| 8 | `beneficial_owner_information` |
| 9 | `business_relationship_purpose` |
| 10 | `business_relationship_nature` |
| 11 | `ownership_and_control_information` |
| 12 | `risk_information` |

The exact fields used may vary depending on the customer and the banking
service being provided.

### Telecommunications

The following standardized fields are supported by the telecommunications
onboarding requirements:

| Field ID | Standardized Field |
|---:|---|
| 0 | `customer_name` |
| 1 | `identification_document_type` |
| 2 | `identification_document_number` |
| 3 | `date_of_birth` |
| 4 | `gender` |
| 5 | `physical_address` |
| 6 | `postal_address` |
| 7 | `subscriber_number` |

Additional fields may apply depending on the subscriber type and registration
requirements.

---

## 3. Field Naming Rules

The vocabulary uses standardized field names so that the same concept is
represented consistently across industries.

The following naming rules apply:

1. Field names use lowercase letters.
2. Words are separated using underscores.
3. The same concept uses the same field name across industries.
4. Each standardized field has one unique numerical ID.
5. Industry-specific requirements are represented through the industry
   mapping rather than by creating duplicate field IDs.

For example, identification document information is represented using:

```text
identification_document_type
identification_document_number