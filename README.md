# UniHub

UniHub is a small Java web app for students to browse dorm listings and find roommates.

## Run

```powershell
javac -d out src/DormMateServer.java
java -cp out DormMateServer
```

Then open `http://localhost:8080`.

## What it includes

- A dorm listing board with seeded student-friendly housing examples
- A roommate board with budget, move-in date, and lifestyle information
- Two forms that let users publish new dorm listings and roommate posts
- A dependency-free Java backend using the built-in `HttpServer`

## Notes

- Data is stored in memory while the server is running
- Static files live in `public/`
