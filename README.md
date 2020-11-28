# KAFKA MONGODB OUTBOX TRANSFORMER

This is a Kafka Streams application which is part of a implementation of [Transactional Outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html) with MongoDB and Kafka.

The implementation overview is:

1. Applications save data in MongoDB and add a "outbox" array field with messages that will be published to Kafka. Example:

```json
{
   "_id":"4c152a44-f49e-44f8-b266-5e79644f1513",
   "createdAt":"2020-11-28T16:38:31.096Z",
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
            "text":"this is an arbitraty field"
         }
      }
   ]
}
```

2. Via Kafka Connect and MongoDB Kafka Connector, documents inserted/replaced/updated and containing and non-empty "outbox" array field will be published to Kafka in a first stage topic, let's call that topic "cdc.mongodb.outbox".

3. This Kafka Streams application (Outbox Transformer), will consume and process the previous topic "cdc.mongodb.outbox". The output will be one message for each element in the "outbox" array field in the original document. The destination topic for each message is determined by the field "targetTopic" in the outbox message.
