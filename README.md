# unfolds

Live at `unfolds.oskarth.com`. Code structure largely inspired by
@swannodette's The Immutable Stack
[code examples](https://github.com/KitchenTableCoders/immutable-stack).

Feedback to [@oskarth](https://twitter.com/oskarth).

## What is it?

See the
[introductory blog post](http://blog.oskarth.com/unfolds-a-jungle-of-ideas-prototype).

## TODO

[] Make the link field bigger.

[] Add stuff from Carlos convo.

[] Multi-word links.


[] Search text instead of just titles.


[] Save entries for easier linking and ranking.


Dev todos:

[] Backup datomic db.


[] Uberjar with REPL for shorter deploys (daemontools).


[] Non por80, nginx reverse proxy.


# Deployment

`git pull` from server

`lein cljsbuild once release`

`bin/transactore config/samples/free-transactor-template.properties`

`lein ring server 80`
