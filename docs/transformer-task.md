# Transformer Task

## Purpose

Define the input and output of the Transformer used by the Sales Agent
Onboarding Guidance System.

The Transformer will use previous onboarding events to predict the next
onboarding field during an onboarding session.

## Transformer Input

The Transformer receives the previous sequence of onboarding events from
the current onboarding session.

The sequence may contain information such as:

- Industry
- Field
- Action
- Time spent
- Error status
- Whether the field was revisited
- Completion status

The input represents what has already happened during the onboarding
session.

## Transformer Output

The Transformer predicts the next onboarding field that should be handled
in the onboarding session.

The output is therefore a single predicted onboarding field.

## Example

For a banking onboarding session, the previous onboarding events might
show:

1. Full name entered.
2. Identification details entered.
3. Identity verification completed.

The Transformer uses this sequence to predict the next onboarding field.

```text
Previous onboarding events
        ↓
    Transformer
        ↓
Next onboarding field