# Onboarding Fields and Requirements

## Purpose

Extract the onboarding fields, validation checks, and onboarding steps supported by the industry references collected in the previous issue.

These definitions provide the structured information needed to create onboarding events and realistic onboarding sessions for future training data.

They do not define one fixed onboarding sequence for every customer.

The Transformer will learn patterns from onboarding event sequences collected from onboarding sessions.

## 1. Banking

### Onboarding Fields

The banking reference identifies information and activities related to customer due diligence.

| Onboarding field | Description |
|---|---|
| Customer identification | Information used to identify the customer. |
| Identity information | Information used to verify the customer's identity. |
| Beneficial-owner information | Information identifying the natural persons behind a legal person or legal arrangement, where applicable. |
| Business relationship purpose | Information about the purpose of the business relationship. |
| Business relationship nature | Information about the intended nature of the business relationship. |
| Ownership and control information | Information about ownership and control structures, where applicable. |

### Validation Checks

The reference requirements support checks such as:

1. Verify the customer's identity.
2. Where applicable, identify and verify the beneficial owner.
3. Where applicable, verify information about ownership and control.
4. Check information used to understand the purpose and intended nature of the business relationship.
5. Perform additional customer due diligence where required by the customer's relationship or risk profile.

### Onboarding Steps

1. Identify the customer.
2. Verify the customer's identity.
3. Identify and verify the beneficial owner where applicable.
4. Obtain information about the purpose and intended nature of the business relationship.
5. Continue customer due diligence based on the customer's relationship and risk profile.

These steps are reference onboarding activities. They do not represent one fixed sequence for every banking customer.

## 2. Telecommunications

### Onboarding Fields

The 2025 telecommunications subscriber registration regulations identify registration information and identification documents required for different subscriber situations.

| Onboarding field | Description |
|---|---|
| Full name | Subscriber identification information. |
| Identification document information | Information from the required identification document. |
| Date of birth | Subscriber date of birth, where applicable. |
| Gender | Subscriber gender, where applicable. |
| Physical address | Subscriber physical address. |
| Postal address | Subscriber postal address, where applicable. |
| Subscriber number | Telephone or service number associated with the registration. |
| Identification document | Original identification document required for verification. |
| Subscriber type | Information used to determine the applicable registration requirements. |

Additional fields or documents may apply depending on the subscriber type.

Examples include:

- Child
- Kenyan citizen
- Foreign national
- Refugee
- Stateless person
- Company

### Validation Checks

The regulations support checks such as:

1. Check that the required identification document is provided.
2. Verify the registration information provided.
3. Authenticate the identification documents against the relevant government database.
4. Check that the registration information corresponds with the identification documents.
5. Update registration information where a change is reported.
6. Check that required registration information has been entered before registration is completed.

### Onboarding Steps

1. Collect the subscriber's registration information.
2. Collect the required identification documents.
3. Verify the provided registration information.
4. Enter the registration particulars.
5. Update information where necessary.
6. Maintain the registration record.
7. Complete subscriber registration.

These steps are reference onboarding activities. They do not represent one fixed sequence for every subscriber.

## 3. Relationship to Onboarding Events

The extracted fields, validation checks, and onboarding steps provide the information needed to represent actions during an onboarding session as onboarding events.

For example:

```text
Onboarding field: identification document
Action: entered
Validation status: valid
Revisited: false