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
model. An admin-created field can use the same `FormField` constructor with
its own rule list, for example:

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
