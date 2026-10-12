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
optional in that configured form. An admin-created field can use the same
`FormField` constructor with its own rule list, for example:

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
The screen sends each text change to `updateField`, observes the live state,
and renders mapper messages under the matching input. Continue is enabled only
when every displayed required field has a valid result. Valid displayed values
are saved through `FieldEntryStore`; the current app uses an in-memory store
until a database-backed store is connected.

The controller can also receive a `NextFieldPredictor`. The Android app uses
`TfliteNextFieldPredictor`, which loads `onboarding_model.tflite` and
`preprocessing_meta.json` from app assets and publishes the top recommendation
through `guidanceFlow`. The model receives the valid field history, the
current industry and context, and the last validation status. The screen shows
the recommended field above the form. `NextFieldGuidance` also turns the same
state into an actionable instruction: it asks the agent to correct the first
touched invalid field, names the next required field to collect, or confirms
that all required information is complete. A prediction is used when it points
to an incomplete displayed field; otherwise the form order provides a safe
fallback. The form keeps its fixed order when no predictor is supplied.
