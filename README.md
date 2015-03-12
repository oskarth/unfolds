# unfolds

Live at `unfolds.oskarth.com`

Feedback to @oskarth.

## TODO

[] Multi-word links.
[] Search text instead of just titles.
[] Save entries for easier linking and ranking.

# Deployment

`git pull` from server

`lein cljsbuild once release`

`bin/transactore config/samples/free-transactor-template.properties`

`lein ring server 80`
