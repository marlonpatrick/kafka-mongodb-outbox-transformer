# KAFKA MONGODB OUTBOX TRANSFORMER

This is a Kafka Streams application which is part of a implementation of [Transactional Outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html) with MongoDB and Kafka.

## High level overview

1. Applications save data in MongoDB and add a "outbox" array field with messages that will be published to Kafka. Example:

```javascript
{
   "_id":"4c152a44-f49e-44f8-b266-5e79644f1513",
   "text":"this is an arbitraty field",
   "outbox":[
      {
         "_id":"c24497c5-4ac2-4502-8f54-8d981c8b1578",
         "createdAt":"2020-11-28T16:38:31.097Z",
         "entityName":"MyEntity",
         "entityId":"4c152a44-f49e-44f8-b266-5e79644f1513",
         "messageName":"MyEntityCreatedEvent",
         "targetTopic":"kafka.target.topic.name",
         "payload":{
            // Here, you put anything you want
            "text":"this is an arbitraty field"
         }
      }
   ]
}
```

2. Via Kafka Connect and MongoDB Kafka Connector, documents inserted/replaced/updated and containing a non-empty "outbox" array field will be published to Kafka in a first stage topic, let's call that topic "cdc.mongodb.outbox".

3. **This Kafka Streams application (Outbox Transformer)**, will consume and process the previous topic "cdc.mongodb.outbox". The output will be one message for each element in the "outbox" array field in the original document. The destination topic for each message is determined by the field "targetTopic" in the outbox message.

## Input Message Formats

There are two expected input message formats: one for insert/replace operations and other for update operations.

Both are generated by the MongoDB Kafka Connector through the correct settings.

1. Insert or Replace operations input message format example


```javascript
{
   "_id":{
      "_data":"825FC27C2D000000022B022C0100296E5A10046FA0DB4434634F4B88BB8EE0F747007C465A5F6964005A1004DD33D10D5AAC423CB6D90BE7914B1A9E0004"
   },
   "operationType":"insert",
   "clusterTime":{
      "$timestamp":{
         "t":1606581293,
         "i":2
      }
   },
   "fullDocument":{
      "_id":"3TPRDVqsQjy22QvnkUsang==",
      "text":"this is an arbitraty field",
      "outbox":[
         {
            "_id":"MSRJCs07SFy4sMpopdRvEA==",
            "createdAt":"2020-11-28T16:34:53.631Z",
            "entityName":"MyEntity",
            "entityId":"3TPRDVqsQjy22QvnkUsang==",
            "messageName":"MyEntityCreatedEvent",
            "targetTopic":"kafka.target.topic.name",
            "payload":{
                // Here, you put anything you want
                "text":"this is an arbitraty field",
            }
         }
      ]
   },
   "ns":{
      "db":"mongodb-source-database-name",
      "coll":"mongodb-source-collection-name"
   },
   "documentKey":{
      "_id":"3TPRDVqsQjy22QvnkUsang=="
   }
}
```

2. Update operations input message format example

```javascript
{
   "_id":{
      "_data":"825FC27C2D000000022B022C0100296E5A10046FA0DB4434634F4B88BB8EE0F747007C465A5F6964005A1004DD33D10D5AAC423CB6D90BE7914B1A9E0004"
   },
   "operationType":"insert",
   "clusterTime":{
      "$timestamp":{
         "t":1606581293,
         "i":2
      }
   },
   "updateDescription": {
        "updatedFields": {
            "outbox":[
                {
                    "_id":"MSRJCs07SFy4sMpopdRvEA==",
                    "createdAt":"2020-11-28T16:34:53.631Z",
                    "entityName":"MyEntity",
                    "entityId":"3TPRDVqsQjy22QvnkUsang==",
                    "messageName":"MyEntityUpdatedEvent",
                    "targetTopic":"kafka.target.topic.name",
                    "payload":{
                        // Here, you put anything you want
                        "text":"this is an arbitraty field",
                    }
                }
            ]
        },
   },
   "ns":{
      "db":"mongodb-source-database-name",
      "coll":"mongodb-source-collection-name"
   },
   "documentKey":{
      "_id":"3TPRDVqsQjy22QvnkUsang=="
   }
}
```

## Input Message Key

It's recomended to use the original document _id as the message key. This application (Outbox Transformer) don't touch in the message key, in other words, the output messages has the same key as the input messages.

## MongoDB Kafka Connector (v1.3) settings

Here is a minimal settings to produce messages in correct formats:

```properties

connector.class=com.mongodb.kafka.connect.MongoSourceConnector

# The will generate a json message which is not tightly coupled to MongoDB (BSON).
# It's related to Relaxed Extended JSON and relaxes additional data types such as Dates as strings,
# Object ID as hex strings, and Binary values as base64 encoded strings.
output.json.formatter=com.mongodb.kafka.connect.source.json.formatter.SimplifiedJson

# When operationType=update, fullDocument is empty and only a delta describing the changes
# will be appended in the message
change.stream.full.document=default

# Original message key will be in avro format
output.format.key=schema

# That message key avro format put the original document _id in the message key
# Will generate a value like {"documentKey": {"_id": "pwZ/jvRMTVyqbXiePoNDaQ=="}}
output.schema.key={ "name":"DocumentKey", "type":"record", "namespace":"outbox.mongodb.avro", "fields": [ {"name": "documentKey", "type": {"name": "documentKeyField","type": "record", "fields": [ { "name":"_id","type": "string"} ] } }] }

# Message key transformations to extract only the _id value without any other json structure
transforms=ExtractDocumentKey,ExtractDocumentIdKey

# Example Input: {"documentKey": {"_id": "pwZ/jvRMTVyqbXiePoNDaQ=="}}
# Example Output: {"_id": "pwZ/jvRMTVyqbXiePoNDaQ=="}
transforms.ExtractDocumentKey.field=documentKey
transforms.ExtractDocumentKey.type=org.apache.kafka.connect.transforms.ExtractField$Key

# Example Input: {"_id": "pwZ/jvRMTVyqbXiePoNDaQ=="}
# Example Output: pwZ/jvRMTVyqbXiePoNDaQ==
transforms.ExtractDocumentIdKey.field=_id
transforms.ExtractDocumentIdKey.type=org.apache.kafka.connect.transforms.ExtractField$Key

# Work's with output.json.formatter=SimplifiedJson
output.format.value=json

# The message value will be a simple string instead a avro or json schema
value.converter=org.apache.kafka.connect.storage.StringConverter

# This pipeline filter the supported operations and ensures the correct schema,
# either in the insert/replace or in the update operations
pipeline=[{"$match":{"operationType": {"$in":[ "insert", "replace", "update"]}}}, {"$match": {"$or": [{"fullDocument.outbox":{"$exists":"true", "$type":"array", "$ne":[]} }, {"updateDescription.updatedFields.outbox":{"$exists":"true", "$type":"array", "$ne":[]}}]}}, {"$match":{"$or": [{"fullDocument.outbox._id":{"$exists":"true"}}, {"updateDescription.updatedFields.outbox._id":{"$exists":"true"}}]}},{"$match":{"$or": [{"fullDocument.outbox.createdAt":{"$exists":"true"}}, {"updateDescription.updatedFields.outbox.createdAt":{"$exists":"true"}}]}},{"$match":{"$or": [{"fullDocument.outbox.entityName":{"$exists":"true" }}, {"updateDescription.updatedFields.outbox.entityName":{"$exists":"true" }}]}}, {"$match":{"$or": [{"fullDocument.outbox.entityId":{"$exists":"true" }}, {"updateDescription.updatedFields.outbox.entityId":{"$exists":"true" }}]}},{"$match":{"$or": [{"fullDocument.outbox.messageName":{"$exists":"true"}}, {"updateDescription.updatedFields.outbox.messageName":{"$exists":"true"}}]}}, {"$match":{"$or": [{"fullDocument.outbox.payload":{"$exists":"true" }}, {"updateDescription.updatedFields.outbox.payload":{"$exists":"true" }}]}}]
```
