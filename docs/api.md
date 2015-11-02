# Temporary OpenCompany API Documentation

## Company List

```
GET /companies
```
returns: application/vnd.collection+vnd.open-company.company+json;version=1

## Company

### Listing a company:

```
GET /companies/<slug>
GET /companies/<slug>?as-of=<timestamp>
```
returns: `application/vnd.open-company.company.v1+json`

### Creating a company:

```
PUT /companies/<slug>
```
accepts: `application/vnd.open-company.company.v1+json`
returns: `application/vnd.open-company.company.v1+json`

### Updating a company:

```
PATCH /companies/<slug>
```
accepts: `application/vnd.open-company.company.v1+json`
returns: `application/vnd.open-company.company.v1+json`

Note-worthy: `PATCH`ing just `sections` property to change order of sections

Note-worthy: `PATCH`ing just `sections` property to remove a sections

Note-worthy: `PATCH`ing `sections` and sending a new section at the same time to add a new section, eg.:
```json
{
  "sections" : {"progress": ["update", "challenges", "help", "growth", "finances"], "company": ["mission", "values"]},
  "help" : {
    "title" : "Asks",
    "body" : "<p>...</p>"
  }
}
```

### Removing a company:

```
DELETE /companies/<slug>
```

TBD what this means. All the implications.


## Sections

### Getting a section:

```
GET /companies/<slug>/<section-name>
GET /companies/<slug>/<section-name>?as-of=<timestamp>
```
returns: `application/vnd.open-company.section.v1+json`

### Creating a section:

`PATCH` the company. See updating a company above.

### Revising a section:

```
PATCH /companies/<slug>/<section-name>
```
accepts: `application/vnd.open-company.section.v1+json`
returns: `application/vnd.open-company.section.v1+json`

Note-worthy: `PATCH`ing just `body` property to revise the section content.

Note-worthy: `PATCH`ing just `title` property to revise the section's title.

Note-worthy: `PATCH`ing just `notes` property to revise the section's notes.

### Removing a section:

Remove the section name from the `sections` property of the company. See updating a company above.
