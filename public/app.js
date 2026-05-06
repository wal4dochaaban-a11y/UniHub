const listingGrid = document.getElementById("listingGrid");
const roommateGrid = document.getElementById("roommateGrid");
const listingCount = document.getElementById("listingCount");
const roommateCount = document.getElementById("roommateCount");
const statusMessage = document.getElementById("statusMessage");

const listingForm = document.getElementById("listingForm");
const roommateForm = document.getElementById("roommateForm");

async function loadListings() {
  const response = await fetch("/api/listings");
  const data = await response.json();

  listingCount.textContent = data.listings.length;
  listingGrid.innerHTML = data.listings.map((listing) => `
    <article>
      <div class="card-topline">
        <span class="pill accent">${listing.badge}</span>
        <span class="pill">${listing.distance}</span>
      </div>
      <h3>${listing.title}</h3>
      <p>${listing.campus}</p>
      <div class="meta-row">
        <span class="price">$${listing.price}</span>
        <span class="pill">${listing.beds} bed</span>
      </div>
      <p>${listing.description}</p>
      <div class="tag-row">
        ${listing.amenities.split(",").map((item) => `<span class="pill">${item.trim()}</span>`).join("")}
      </div>
    </article>
  `).join("");
}

async function loadRoommates() {
  const response = await fetch("/api/roommates");
  const data = await response.json();

  roommateCount.textContent = data.roommates.length;
  roommateGrid.innerHTML = data.roommates.map((post) => `
    <article>
      <div class="card-topline">
        <span class="pill sea">${post.school}</span>
        <span class="pill">Move in ${post.moveInDate}</span>
      </div>
      <h3>${post.name}</h3>
      <div class="meta-row">
        <span class="price">$${post.budget}</span>
        <span class="pill">${post.lifestyle}</span>
      </div>
      <p>${post.bio}</p>
      <div class="tag-row">
        <span class="pill sea">${post.match}</span>
      </div>
    </article>
  `).join("");
}

async function postForm(form, endpoint, successText, reloadFn) {
  const formData = new FormData(form);
  const payload = new URLSearchParams(formData);

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
    },
    body: payload
  });

  if (!response.ok) {
    throw new Error("Request failed");
  }

  form.reset();
  statusMessage.textContent = successText;
  await reloadFn();
}

listingForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    await postForm(listingForm, "/api/listings", "Dorm listing published.", loadListings);
  } catch (error) {
    statusMessage.textContent = "Could not publish the dorm listing.";
  }
});

roommateForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    await postForm(roommateForm, "/api/roommates", "Roommate profile published.", loadRoommates);
  } catch (error) {
    statusMessage.textContent = "Could not publish the roommate profile.";
  }
});

Promise.all([loadListings(), loadRoommates()]).catch(() => {
  statusMessage.textContent = "Could not load the site data.";
});
