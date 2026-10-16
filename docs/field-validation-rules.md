# Field validation rules

The Android validation engine is in
`app/src/main/java/com/example/salesonboarding/validation`.

`FormField` contains a list of `ValidationRule` values. A field is validated
by applying that list in order to its `FieldEntry`. Rules are generic
(`Required`, `RegexPattern`, `EmailFormat`, `KenyanPhoneFormat`,
`DateFormatAndMinimumAge`, `OneOf`, `PositiveNumber`, `MustBeTrue`, and
`RequiredIf`); they do not contain field-name branches. `ValidationContext`
provides other field values for conditional rules.

The `KnownFields` catalogue defines the 33 fields currently understood by the
model. Admin changes to an existing field are represented by
`FieldRuleConfiguration`: each `FieldRuleOverride` can add or remove
individual rules while leaving the catalogue defaults available as a
baseline. Removing `Required` intentionally makes a required-by-default field
optional in that configured form. The admin required-field toggle removes both
`Required` and `NotBlank`, so disabling requiredness does not leave a
contradictory blank-value rule behind.

The admin dashboard can also add custom fields. Each has a generated ID based
on its name, a description, an input type (`text`, `number`, `date`, `email`,
`phone`, `boolean`, or `choice`), and selected rules (`Required`, `NotBlank`,
`EmailFormat`, `KenyanPhoneFormat`, `PositiveNumber`, `MustBeTrue`, or
`DateFormatAndMinimumAge(18)`). Choice fields also save their comma-separated
approved options and validate/select from that list. A custom field is stored
separately from `KnownFields` and appended to future onboarding forms. Its
description is shown below its input. These field definitions are persisted under
`fieldDefinitions/{fieldId}` in Firebase Realtime Database.

For code-created fields, use the same `FormField` constructor:

```kotlin
FormField(
    fieldId = "admin_reference",
    fieldName = "Reference",
    fieldType = "text",
    isRequired = true,
    validationRule = listOf(Required, RegexPattern("^REF-\\d{4}$"))
)
```

Optional fields accept a blank value unless a `RequiredIf` rule makes them
applicable. Conditional fields use values in `ValidationContext`, such as
`service_type = "mobile"` or `customer_type = "business"`.

Rule configuration does not change field IDs, vocabulary indices, or model
inputs, so administrators can change validation policy without retraining the
model. If validation status is later included as a model feature, that should
be treated as a separately versioned input contract.

`LiveValidationSession` provides the screen-independent live layer for the
form. Call `updateField(fieldId, value)` whenever an answer changes and observe
`stateFlow`. Values are published immediately. Validation runs after a 300 ms
pause and updates only the changed field and fields whose rules depend on it.
For example, changing `service_type` re-checks the service-dependent fields.
The session accepts a `FieldRuleConfiguration`, so admin overrides are applied
to live checks as well.

`LiveValidationState.touchedFieldIds` records fields that the agent has
changed. `ValidationErrorDisplayMapper` only returns failed results for those
fields, so an untouched required field does not show an error before the agent
reaches it. If the agent enters a value and then clears it, the field remains
touched and its required-field message is returned. The mapper returns plain
Kotlin data and does not depend on Compose or another UI framework.

The onboarding flow uses `OnboardingFormController` and `OnboardingScreen`.
The screen sends each answer change to `updateField`, observes the live state,
and renders mapper messages under the matching input. Continue is enabled only
when every required field has a valid result. Valid nonblank values are saved
through `FirebaseFieldEntryStore` under
`onboardingSessions/{sessionId}/entries/{fieldId}`.

`OnboardingPlaybooks` defines the standard ordered telecom and banking steps.
Each playbook revision is a SHA-256 fingerprint of its ordered field IDs,
names, input types, descriptions and validation rules. A session stores its
playbook ID, revision and field-definition snapshot, so later admin changes do
not obscure which instructions the agent followed. Fields with `OneOf` rules
are presented as controlled selections instead of free-text answers.

The readiness summary reports how many required fields are valid, names
unfinished required steps, and blocks completion when an entered optional
value fails its rules. Team analytics uses saved validation state and the
playbook snapshot to estimate progress and find common unfinished steps.

The controller can also receive a `NextFieldPredictor`. The Android app uses
`TfliteNextFieldPredictor`, which loads `onboarding_model.tflite` and
`preprocessing_meta.json` from app assets and publishes the top recommendation
through `guidanceFlow`. Only IDs from the fixed `KnownFields` catalogue are
sent as model history; custom field IDs do not alter the model input
vocabulary. `NextFieldGuidance` asks the agent to correct a touched invalid
field, names the next required field, or confirms that required information is
complete. A prediction is used when it points to an incomplete displayed
field; otherwise the form order provides a safe fallback.
