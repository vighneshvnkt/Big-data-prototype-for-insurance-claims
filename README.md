# big-data-prototype-for-insurance-claims

A big data design prototype REST API for insurance claims developed in spring boot
where the json is passed through curl commands and data is saved in Redis

### Prerequisites

Java 9.0
Spring Tools Suite
Curl
Maven



## Running the example
Run the REST API through STS
Run following commands in CURL
POST command: (goodJSON contains the JSON to be posted)

```
curl -X POST -H "Content-Type: application/json" -d @path/to/goodJSON.json localhost:8090/
```

GET command:

```
curl -H "Authorization: auth_token" -H "Accept: application/json" -H If-None-Match:"etag" localhost:8090/type/id
```

PUT command: (If object exists, objectJSON replaces existing JSON. If it does not exist, objectJSON is appended to existing JSON)

```
curl -X PUT -H "Authorization: auth_token" -H "Content-Type: application/json" -d @path/to/objectJSON.json localhost:8090/id
```

DELETE command: (can delete entire JSON or specific objects)

```
curl -X DELETE -H "Authorization: auth_token" -H "Content-Type: application/json" localhost:8090/id
```

## Authors

* **Vighnesh Venkatakrishnan**


## License

This project is licensed under the MIT License - see the [LICENSE.md] file for details

## Acknowledgments

* JSONLint
* json-schema.org
* Prof Marwan
* Redis documentation
