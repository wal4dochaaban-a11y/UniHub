# UniHub

UniHub is a small Java web app for students to browse dorm listings and find roommates.

The app uses a real SQLite database file named `unihub.db` for persistent dorm listings and roommate posts.

## Run

```powershell
javac -d out src/DormMateServer.java
java -cp out DormMateServer
```

Then open `http://localhost:8080`.

The server creates `unihub.db` automatically the first time it starts.

## What it includes

- A dorm listing board with seeded student-friendly housing examples
- A roommate board with budget, move-in date, and lifestyle information
- Two forms that let users publish new dorm listings and roommate posts
- A dependency-free Java backend using the built-in `HttpServer`
- A persistent SQLite database with `dorm_listings` and `roommate_posts` tables

## Notes

- Data is stored in `unihub.db`, so submitted posts remain after restarting the server
- SQLite must be available as `sqlite3` on the computer PATH
- Static files live in `public/`
