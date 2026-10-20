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