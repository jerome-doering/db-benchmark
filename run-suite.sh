#!/bin/sh
echo 'Stopping docker-compose\n'
docker-compose down

echo 'Building executable JAR\n'
./gradlew clean jmhJar

echo '\n\n\n\n\n'
echo 'Running mongo benchmarks'
echo '############\n'

docker-compose up -d mongo

until [ $(docker-compose logs --tail='all' mongo  | grep "Waiting for connections" | wc -l) -gt 0 ]; do
	printf '.'
    sleep 1
done

java -jar build/libs/db-benchmark-1.0-SNAPSHOT-jmh.jar MongoRunner  -rff "build/mongo.csv" -o "build/mongo.txt"

docker-compose down

echo '\n\n\n\n\n'
echo 'Running mariadb benchmarks'
echo '############\n'

docker-compose up -d mariadb

until [ $(docker-compose logs --tail='all' mariadb  | grep "ready for connections" | wc -l) -gt 0 ]; do
	printf '.'
    sleep 1
done
sleep 4
java -jar build/libs/db-benchmark-1.0-SNAPSHOT-jmh.jar MariaRunner  -rff "build/maria.csv" -o "build/maria.txt"

docker-compose down


echo '\n\n\n\n\n'
echo 'Running postgres benchmarks'
echo '############\n'

docker-compose up -d postgres

until [ $(docker-compose logs --tail='all' postgres  | grep "database system is ready to accept connections" | wc -l) -gt 0 ]; do
	printf '.'
    sleep 1
done

java -jar build/libs/db-benchmark-1.0-SNAPSHOT-jmh.jar PostgresRunner  -rff "build/postgres.csv" -o "build/postgres.txt"

docker-compose down

echo '\n\n'
echo 'Finished, check the following files for results:'
ls build/*.txt
