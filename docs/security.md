# OpenCompany Security Design

## Overview

The security design needs to account for 6 things: Registration, Authentication and Authorization for UI and 
API usage.

There are also some key security moments:

1. User is created on platform
1. Company comes into existence on platform
1. User is affiliated with company with write access
1. User is affiliated with company with read/comment access
1. User affiliation with company is removed
1. Story is created as a draft
1. Story is created is shared internally
1. Story is created published publicly

It's desirable that the initial security story can be email and Slack focused. Other options such as Google, GitHub, LinkedIn and multi-factor authentication could come later.


## Registration

A user needs to be newly created on the system. This is registration.

There are 6 ways new users can come into the system:

1. A new user (presumably for forum access)
2. A new user creating a new company
3. A new user joining an existing company with Slack auth
4. A new user joining an existing company with email auth
5. A new user joining an existing company with an email invitation
6. A new user joining an existing company with a Slack invitation

#### New Company Creation

When a new company is created, the creator can (optionally) select an email domain and/or Slack organization that gets automatic access. If they select the Slack organization, then they need to setup the TransparencyBot integration.

At anytime a user with write access to the company can invite a user by email address or by Slack (if the TransparencyBot is integrated into their Slack organization).

### UI

UI provides an anauthenticated user with the option to "Sign Up".


### API

#### New User Creation


#### New Company Creation

JWT auth'd `OPTIONS` request on `/company/<TICKER>` let's API client know the user is auth'd to create the company, and the ticker is available

JWT auth'd `PUT` request on `/company/<TICKER>` creates the company.

## Authentication

Once a user is registered, the next question is, how do we recognize them, and trust that it's really them? This is authentication.

A successful authentication results in a JSON web token that can be used as an authentication credential with platform services. [JSON Web Tokens](http://jwt.io/) are an open, industry standard [RFC 7519](https://tools.ietf.org/html/rfc7519) method for representing security claims.

The steps to use JWT for authentication to services are as follows:

1. UI or API client authenticates to the authentication server
1. If authentication is valid, the authentication server encrypts some JWT information into a token string that is returned as the body payload
1. UI or API client then saves the token somewhere and sends it as the `Authorization` header on every request to any service
1. Service decrypts the `Authorization` header, and if decryption is successful, it means the client is authenticated as that user.

### UI


### API


## Authorization

Once a user is authenticated, and we know who they are, the next question is, what can they do? This is authorization.

### Levels

There are 4 levels of possible authorization:

1. Unauthenticated anonymous access
2. Authenticated user with no affiliation to the company
3. Authenticated user with read/comment access to the affiliated company (employee/investor)
4. Authenticated user with write access to the affiliated company (founder/exec)

For the purposes of information access, #1 and #2 are equivalent, they only get access to information that has been published publicly and cannot see comments or create comments.

Access authorized as #3 has access to all company information that is not draft, and can see all comments and can create comments.

Access authorized as #4 has access to all company information that is not draft, and can see all comments and can create comments.

### UI

### API