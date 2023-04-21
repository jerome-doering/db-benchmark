db.createCollection('lookup')

db.lookup.createIndex(
    {
      "identifiers.$**": 1
    },
    {
      name: 'lookup_values'
    }
)

db.lookup.createIndex({"archivalId": 1}, { unique: true })