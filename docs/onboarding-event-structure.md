# Onboarding Event Structure

## Purpose

Define the information recorded for each event during a customer onboarding session.

The event data will be used to record what the sales agent does during onboarding and the current progress of the session.

## Event Information

Each onboarding event records:

- Industry
- Session ID
- Field
- Action
- Time spent
- Error status
- Whether the field was revisited
- Completion status

## Example

A banking onboarding session may produce events such as:

1. Full name entered.
2. ID number entered.
3. ID number corrected after an error.
4. Identity verification completed.

The sequence of events shows how the onboarding session progressed.

## Purpose for the Transformer

The Transformer will learn patterns from sequences of onboarding events.

The event structure allows the training data to contain different:

- Industries
- Field sequences
- Errors
- Revisited fields
- Completion outcomes

The Transformer will use these patterns to support step-by-step guidance during onboarding.

## Persisted event payload and privacy

The Android app's audit events currently persist only event type, field ID, and
timestamp. They do not include customer-entered values or a per-keystroke
presence flag. Validation outcomes are stored separately by field. Submitted
values are saved in the onboarding entry record because they are needed for the
customer application. Do not add raw form values to events, analytics, or logs.

The agent confirms that the customer has been informed before the session is
created; the session stores that confirmation timestamp. This prototype does
not enforce a retention period or delete records automatically.
