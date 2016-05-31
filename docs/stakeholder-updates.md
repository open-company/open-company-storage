# Stakeholder Updates

The API for Stakeholder Updates supports the following:

* initial selected sections for a new company
* data for the live view
* edit the title
* add/remove/reorder live sections
* create/share a stakeholder update (turns live view into a saved prior) 
* list of prior updates
* get a prior update
* remove a prior update
* remove section from the stakeholder updates section property when it's removed from the dashboard


## "Live" View

The template for the "live" and next pending stakeholder update exists as a property of the company with the following
format:

```
:stakeholder-update {
  :title ""
  :sections ["name" "name"]
}
```

A "live" or "pending" stakeholder update can always be shown by showing the title and
the latest contents of each of the included sections in the specified order.
When displaying a stakeholder update, any section that's in the stakeholder update but is not currently in the
dashboard (shouldn't actually happen) should be skipped. Any section that's still a placeholder should be skipped.


## Stakeholder Content Editing / Ordering

The title for the "live" next pending shared stakeholder update can be edited by `PATCH`ing the company.

Sections in the next pending stakeholder update can be added, removed, reordered by `PATCH`ing the company. Only
sections active in the dashboard can be included. Including other sections returns a `422` error.


## Stakeholder Update Creating

A blank POST to `/companies/{slug}/updates` creates a new stakeholder update from the current "live" pending
stakeholeder update and resets the title to blank. The authorized user doing the POST is captured in the created
stakeholder update as the author.


## List of Prior Stakeholder Updates

The list of past stakeholder updates can be retrieved by all users (auth'd and not) at: 

```
/companies/{slug}/updates
```

The response (assuming a user auth'd to the company, otherwise DELETE links will be missing):

```json
{
  "collection" : {
    "version" : "1.0",
    "href" : "/companies/buffer/updates",
    "links" : [
      {
        "rel" : "self",
        "method" : "GET",
        "href" : "/companies/buffer/updates",
        "type" : "application/vnd.collection+vnd.open-company.stakeholder-update+json;version=1"
      },
      {
        "rel" : "company",
        "method" : "GET",
        "href" : "/companies/buffer",
        "type" : "application/vnd.open-company.company+json;version=1"
      }
    ],
    "stakeholder-updates" : [ {
      "title" : "December Update",
      "slug" : "december-update-4a6f",
      "author" : {
        "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
        "name" : "Joel Gascoigne",
        "user-id" : "123456"
      },
      "created-at": "2016-01-02T12:28:20.454Z",
      "links" : [ 
        {
          "rel" : "self",
          "method" : "GET",
          "href" : "/companies/buffer/updates/december-update-4a6f",
          "type" : "application/vnd.open-company.stakeholder-update.v1+json"
        },
        {
          "rel" : "delete",
          "method" : "DELETE",
          "href" : "/companies/buffer/updates/december-update-4a6f"
        }
      ]
    }, {
      "title" : "January Update",
      "slug" : "january-update-65b5",
      "author" : {
        "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
        "name" : "Joel Gascoigne",
        "user-id" : "123456"
      },
      "created-at": "2016-03-08T12:28:20.454Z",
      "links" : [
        {
          "rel" : "self",
          "method" : "GET",
          "href" : "/companies/buffer/updates/january-update-65b5",
          "type" : "application/vnd.open-company.stakeholder-update.v1+json"
        },
        {
          "rel" : "delete",
          "method" : "DELETE",
          "href" : "/companies/buffer/updates/january-update-65b5"
        }
      ]
    }, {
      "title" : "February Update",
      "slug" : "february-update-d786",
      "author" : {
        "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
        "name" : "Joel Gascoigne",
        "user-id" : "123456"
      },
      "created-at": "2016-03-12T12:28:20.454Z",
      "links" : [
        {
          "rel" : "self",
          "method" : "GET",
          "href" : "/companies/buffer/updates/february-update-d786",
          "type" : "application/vnd.open-company.stakeholder-update.v1+json"
        },
        {
          "rel" : "delete",
          "method" : "DELETE",
          "href" : "/companies/buffer/updates/february-update-d786"
        }
      ]
    }]
  }
}
```

If the user is auth'd to the company, the stakeholder update list response's HATEOAS links will include a `POST`
link to share a new stakeholder update:

```json
...
    "links" : [ 
      {
        "rel" : "self",
        "method" : "GET",
        "href" : "/companies/buffer/updates",
        "type" : "application/vnd.collection+vnd.open-company.stakeholder-update+json;version=1"
      }, {
        "rel" : "share",
        "method" : "POST",
        "href" : "/companies/buffer/updates"
      },
      {
        "rel" : "company",
        "method" : "GET",
        "href" : "/companies/buffer",
        "type" : "application/vnd.open-company.company+json;version=1"
      }
    ]
...
```

## Stakeholder Update Retrieval

A stakeholder update URL from the Location response to a `POST` or from the stakeholder update list can be retrieved
by `GET`ing the provided URL:

```
/companies/{slug}/updates/{slugified-title}-{uuid}
```

The response:

```json
{
  "name" : "Buffer",
  "slug": "january-update-65b5",
  "logo" : "https://open-company-assets.s3.amazonaws.com/buffer.png",
  "logo-width" : 300,
  "logo-height" : 96,
  "description" : "A better way to share on social media.",
  "created-at" : "2016-02-12T12:28:20.454Z",
  "title": "January Update",
  "sections" : ["product", "team", "help"],
  "author" : {
    "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
    "name" : "Joel Gascoigne",
    "user-id" : "123456"
  },
  "product" : {
    "updated-at" : "2015-11-20T08:00:46.000Z",
    "headline" : "We launched Pablo 2.0!",
    "title" : "Product",
    "author" : {
      "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
      "name" : "Joel Gascoigne",
      "user-id" : "123456"
    },
    "image" : "https://open-company.s3.amazonaws.com/product.svg",
    "body" : "\n        <p>We launched <a href=\"https://blog.bufferapp.com/pablo-images-for-instagram-pinterest-twitter-facebook\">Pablo 2.0!</a>\n        More than 500,000 Pablo images have been created so far, and we're excited to keep going with our quick\n        images tool.</p>\n        <img src=\"https://open.buffer.com/wp-content/uploads/2015/11/Pablo-2-launch-social-media-images-800x643.png\">\n        <p>We also made several improvements to Buffer for Business, including the move from 7 day to 30 day trials,\n        a new groups UI, the ability to search profiles and improved reliability/speed of analytics.</p>\n        <ul>\n          <li>Calendar View feature in beta (and <a href=\"https://blog.bufferapp.com/social-media-calendar\">launched in November</a>)</li>\n          <li>Awesome Plan ($10/mo) MRR: $379,189 (+2.3%)</li>\n          <li>Buffer for Business ($50+/mo) MRR: $272,227 (-0.3%) (a result of our move to 30-day trials)</li>\n          <li>New landing page experiment (awaiting results)</li>\n          <li>Multiple photo upload feature for Twitter</li>\n        </ul>"
  },
  "team" : {
    "updated-at" : "2015-11-20T08:00:46.000Z",
    "headline" : "We went totally remote and distributed.",
    "title" : "Team and hiring",
    "author" : {
      "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
      "name" : "Joel Gascoigne",
      "user-id" : "123456"
    },
    "image" : "https://open-company.s3.amazonaws.com/team.svg",
    "body" : "\n          <p>In October, we <a href=\"https://open.buffer.com/no-office/\">closed Buffer's physical office</a>\n          to go entirely remote and distributed.</p>\n          <p>As we grow, we started experimenting with\n          <a href=\"https://open.buffer.com/why-we-ask-our-team-to-grade-us-every-month-buffers-october-hiring-report/\">NPS</a>\n          to get the \"pulse\" of Buffer regularly.</p>\n          <p>We also evolved some of our team tools: we left HipChat & Sqwiggle for Slack & Zoom and joined Okta as a\n          security solution. On the transparency front, Leo shared more details on\n          <a href=\"https://open.buffer.com/explaining-equity/\">How We Explain Stock Options to Team Members</a>.</p>\n          <ul>\n            <li>56 team members across the world in 40 cities (full time + bootcampers)</li>\n            <li>In October, 9 teammates completed their bootcamp and 6 of them will be joining us full time. We're\n            so excited to welcome:\n            <ul>\n              <li><a href=\"https://twitter.com/kellybakes\">Kelly, Happiness Hero</a></li>\n              <li><a href=\"https://twitter.com/_alexray\">Alex, Front-End Engineer</a></li>\n              <li><a href=\"https://twitter.com/mwermuth\">Marcus, iOS Engineer</a></li>\n              <li><a href=\"https://twitter.com/dearlorenz\">Lorenz, Designer</a></li>\n              <li><a href=\"https://twitter.com/RoyBoss\">Roy, Customer Development</a></li>\n              <li><a href=\"https://twitter.com/alfred_lua\">Alfred, Community Champion</a></li>\n            </ul>8 new team members started their bootcamp in October.</li>\n            <li>At least 8 more people will be starting bootcamp throughout November.</li>\n            <li>We looked into 1,992 hiring conversations in October (+29% MoM)</li>\n          </ul>\n          <p>In November, we're hiring across <a href=\"https://buffer.com/journey\">12 open positions</a>, listed\n          on <a href=\"https://buffer.com/journey\">Buffer's Journey page</a>.</p>"
  },
  "help" : {
    "updated-at" : "2015-11-20T08:00:46.000Z",
    "headline" : "One quick ask.",
    "title" : "Asks",
    "author" : {
      "image" : "https://secure.gravatar.com/avatar/46c1c756f36549c2dea0253e1e025053?s=96&d=mm&r=g",
      "name" : "Joel Gascoigne",
      "user-id" : "123456"
    },
    "image" : "https://open-company.s3.amazonaws.com/help.svg",
    "body" : "<p>One quick ask: Recently we launched a brand new feature:\n        <a href=\"https://blog.bufferapp.com/social-media-calendar\">Social Media Calendar</a>.\n        We'd love your help to spread the word. <a href=\"https://twitter.com/intent/tweet?text=Thrilled%20to%20see%20@buffer%27s%20Social%20Media%20Calendar!%20Great%20to%20manage%20social%20media%20at-a-glance:%20https://blog.bufferapp.com/social-media-calendar%20pic.twitter.com/Y8XdRyU9ie\">Tweet</a>\n        or <a href=\"https://buffer.com/add?url=https%3A%2F%2Fblog.bufferapp.com%2Fsocial-media-calendar&text=Thrilled%20to%20see%20@buffer%27s%20Social%20Media%20Calendar!%20Great%20to%20manage%20social%20media%20at-a-glance:&picture=https%3A%2F%2Fbufferblog-wpengine.netdna-ssl.com%2Fwp-content%2Fuploads%2F2015%2F11%2Fcalendar-launch-600x410%402x.png&version=2.13.12&placement=hover_button_image\">Buffer</a>!</p>"
  },
  "links" : [ 
    {
      "rel" : "self",
      "method" : "GET",
      "href" : "/companies/buffer/updates/january-update-65b5",
      "type" : "application/vnd.open-company.stakeholder-update.v1+json"
    },
    {
      "rel" : "delete",
      "method" : "DELETE",
      "href" : "/companies/buffer/updates/january-update-65b5"
    },
    {
      "rel" : "company",
      "method" : "GET",
      "href" : "/companies/buffer",
      "type" : "application/vnd.open-company.company.v1+json"
    }]
}
```

## Stakeholder Update Removal

A user auth'd to the company can remove the stakeholder update using the included HATEOAS link to `DELETE`:

```json
...
    "stakeholder-updates" : [ {
      "name" : "December Update",
      "created-at": "2016-01-02T12:28:20.454Z",
      "links" : [ {
        "rel" : "self",
        "method" : "GET",
        "href" : "/companies/buffer/updates/december-update-4a6f",
        "type" : "application/vnd.open-company.stakeholder-update.v1+json"
      }, {
        "rel" : "self",
        "method" : "DELETE",
        "href" : "/companies/buffer/updates/december-update-4a6f"
      } ]
    },
...
```

## Web UI URLs

"Live" stakeholder update: `/{company-slug}/updates`

Prior stakeholder update: `/{company-slug}/updates/{slugified-title}-{uuid}`