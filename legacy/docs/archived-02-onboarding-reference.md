# 2. Onboarding reference

The app supports two reference industries: banking and telecommunications.
The workflows are simplified guidance models, not a replacement for an
institution's legal or operational procedures.

## Banking

Typical activities include customer identification, identity verification,
beneficial-owner checks where applicable, and recording the purpose and nature
of the business relationship.

## Telecommunications

Typical activities include subscriber registration, identification-document
capture, contact details, service selection, verification, and completion.
The exact requirements depend on the subscriber type and service.

## Fields and validation

The field catalogue is defined in
`app/src/main/java/com/example/salesonboarding/validation/KnownFields.kt`.
It currently contains 33 model-compatible fields.

Reusable rules include required values, email and Kenyan phone formats,
dates and minimum age, positive numbers, allowed choices, confirmations, and
conditional requirements. Admin overrides are represented by
`FieldRuleConfiguration` and do not change model field IDs.

The onboarding session records field updates, validation results, revisits,
duration, and completion status.
