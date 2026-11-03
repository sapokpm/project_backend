#!/usr/bin/env python3
"""
Builds the Postman collection and environment (the two JSON files next to this script).
Keeping the source as code means the ~130 requests stay consistent. Run:  python3 postman/build_collection.py
"""
import json, uuid, re
from pathlib import Path

OUT = Path(__file__).resolve().parent
NS = uuid.UUID("6f1d2c3a-0000-4000-8000-00000000c0de")  # stable ids: re-running does not change the files


def sid(label):
    return str(uuid.uuid5(NS, label))


# ------------------------------------------------------------------------ script snippets
def status(code, label=None):
    return f'pm.test("Status code is {code}{(" - " + label) if label else ""}", function () {{ pm.response.to.have.status({code}); }});'


def check(label, js_expr):
    """A test asserting one JavaScript boolean expression about `json` (the parsed body)."""
    return f'pm.test({json.dumps(label)}, function () {{ var json = pm.response.json(); pm.expect(Boolean({js_expr})).to.be.true; }});'


def check_text(label, js_expr):
    return f'pm.test({json.dumps(label)}, function () {{ var text = pm.response.text(); pm.expect(Boolean({js_expr})).to.be.true; }});'


def setvar(name, js_expr):
    return f'pm.environment.set("{name}", {js_expr});'


def error_shape(code, error_name):
    return [
        status(code),
        check("Error body has the standard shape",
              f'json.status === {code} && json.error === "{error_name}" && typeof json.message === "string" '
              f'&& typeof json.path === "string" && typeof json.timestamp === "string" && typeof json.details === "object"'),
        check_text("No stack trace or SQL in the body", '!/Exception|\\bat com\\.|org\\.springframework|select .* from|SQLState/i.test(text)'),
    ]


ERR = {400: "BAD_REQUEST", 401: "UNAUTHORIZED", 402: "PAYMENT_REQUIRED", 403: "FORBIDDEN", 404: "NOT_FOUND", 409: "CONFLICT"}


def fail(code):
    return error_shape(code, ERR[code])


# ------------------------------------------------------------------------ request builder
def bearer(var):
    return {"type": "bearer", "bearer": [{"key": "token", "value": "{{" + var + "}}", "type": "string"}]}


def req(name, method, path, tests, auth="user", body=None, pre=None, headers=None, query=None, desc=None):
    path_parts = [p for p in path.strip("/").split("/")]
    q = query or {}
    raw = "{{baseUrl}}/" + "/".join(path_parts) + (("?" + "&".join(f"{k}={v}" for k, v in q.items())) if q else "")
    request = {
        "method": method,
        "header": [{"key": k, "value": v} for k, v in (headers or {}).items()],
        "url": {"raw": raw, "host": ["{{baseUrl}}"], "path": path_parts,
                **({"query": [{"key": k, "value": str(v)} for k, v in q.items()]} if q else {})},
    }
    if auth == "user":
        request["auth"] = bearer("accessToken")
    elif auth == "admin":
        request["auth"] = bearer("adminToken")
    else:
        request["auth"] = {"type": "noauth"}
    if body is not None:
        request["body"] = {"mode": "raw", "raw": body if isinstance(body, str) else json.dumps(body, indent=2),
                           "options": {"raw": {"language": "json"}}}
        request["header"].append({"key": "Content-Type", "value": "application/json"})
    if desc:
        request["description"] = desc
    events = []
    if pre:
        events.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre if isinstance(pre, list) else [pre]}})
    events.append({"listen": "test", "script": {"type": "text/javascript", "exec": tests}})
    return {"name": name, "event": events, "request": request, "id": sid(name + method + path)}


def folder(name, items, desc=None):
    f = {"name": name, "item": items, "id": sid("folder" + name)}
    if desc:
        f["description"] = desc
    return f


PAGE = lambda: [check("Response is a page (content array, totalElements, size, number)",
                      'Array.isArray(json.content) && typeof json.totalElements === "number" && typeof json.size === "number" && typeof json.number === "number"')]

# ========================================================================== AUTH
auth = folder("Auth", [
    req("Register (new customer)", "POST", "api/auth/register", [
        status(201),
        check("Returns both tokens and a CUSTOMER user", 'json.accessToken && json.refreshToken && json.user.role === "CUSTOMER" && json.tokenType === "Bearer"'),
        check_text("No password or hash in the response", '!/password|\\$2[aby]\\$/i.test(text)'),
        setvar("accessToken", "pm.response.json().accessToken"),
        setvar("refreshToken", "pm.response.json().refreshToken"),
    ], auth=None,
        pre=['pm.environment.set("userEmail", "postman-" + Date.now() + "@example.com");',
             'pm.environment.set("userPassword", "Str0ngPass1");'],
        body='{\n  "email": "{{userEmail}}",\n  "password": "{{userPassword}}",\n  "firstName": "Postman",\n  "lastName": "Tester"\n}',
        desc="Creates a fresh customer with a unique email on every run, so the collection can be run again and again."),
    req("Register - validation failure (bad email, weak password)", "POST", "api/auth/register",
        fail(400) + [check("Names the failing fields", 'json.details.email && json.details.password')],
        auth=None, body='{"email": "not-an-email", "password": "weak", "firstName": "A", "lastName": "B"}'),
    req("Register - duplicate email", "POST", "api/auth/register",
        fail(400) + [check("Names the email field", 'json.details.email')],
        auth=None, body='{"email": "{{userEmail}}", "password": "Str0ngPass1", "firstName": "A", "lastName": "B"}'),
    req("Login", "POST", "api/auth/login", [
        status(200),
        check("Returns tokens", 'json.accessToken && json.refreshToken'),
        setvar("accessToken", "pm.response.json().accessToken"),
        setvar("refreshToken", "pm.response.json().refreshToken"),
    ], auth=None, body='{"email": "{{userEmail}}", "password": "{{userPassword}}"}',
        desc="Stores accessToken and refreshToken in the environment; every other folder uses them."),
    req("Login - wrong password", "POST", "api/auth/login", fail(401), auth=None,
        body='{"email": "{{userEmail}}", "password": "WrongPass1"}'),
    req("Login as admin", "POST", "api/auth/login", [
        status(200),
        check("Is an ADMIN", 'json.user.role === "ADMIN"'),
        setvar("adminToken", "pm.response.json().accessToken"),
    ], auth=None, body='{"email": "admin@shop.local", "password": "Admin123!"}',
        desc="Seeded demo admin. Its token is stored as adminToken and used by the Admin folder."),
    req("Refresh token", "POST", "api/auth/refresh", [
        status(200),
        check("Returns a different refresh token (rotation)", 'json.refreshToken && json.refreshToken !== pm.environment.get("oldRefreshToken")'),
        setvar("accessToken", "pm.response.json().accessToken"),
        setvar("refreshToken", "pm.response.json().refreshToken"),
    ], auth=None, pre='pm.environment.set("oldRefreshToken", pm.environment.get("refreshToken"));',
        body='{"refreshToken": "{{refreshToken}}"}'),
    req("Refresh - old token is dead", "POST", "api/auth/refresh", fail(401), auth=None,
        body='{"refreshToken": "{{oldRefreshToken}}"}'),
    req("Protected endpoint without a token -> 401", "GET", "api/users/me", fail(401), auth=None),
    req("Change password - wrong current password", "POST", "api/auth/change-password", fail(400),
        body='{"currentPassword": "WrongPass1", "newPassword": "N3wStrongPass1"}'),
    req("Change password", "POST", "api/auth/change-password", [
        status(204),
        check_text("No body on 204", 'text === ""'),
        setvar("userPassword", '"N3wStrongPass1"'),
    ], body='{"currentPassword": "{{userPassword}}", "newPassword": "N3wStrongPass1"}'),
    req("Login with the new password", "POST", "api/auth/login", [
        status(200),
        setvar("accessToken", "pm.response.json().accessToken"),
        setvar("refreshToken", "pm.response.json().refreshToken"),
    ], auth=None, body='{"email": "{{userEmail}}", "password": "{{userPassword}}"}'),
    req("Logout (revokes the refresh token)", "POST", "api/auth/logout", [
        status(204),
    ], auth=None, body='{"refreshToken": "{{refreshToken}}"}'),
    req("Refresh after logout -> 401", "POST", "api/auth/refresh", fail(401), auth=None,
        body='{"refreshToken": "{{refreshToken}}"}'),
    req("Login again (tokens for the rest of the run)", "POST", "api/auth/login", [
        status(200),
        setvar("accessToken", "pm.response.json().accessToken"),
        setvar("refreshToken", "pm.response.json().refreshToken"),
    ], auth=None, body='{"email": "{{userEmail}}", "password": "{{userPassword}}"}'),
], desc="Run this folder first. It registers a customer and stores accessToken, refreshToken and adminToken.")

# ========================================================================== USERS
ADDR = '{{"title": "{t}", "country": "Kazakhstan", "city": "{c}", "street": "Abay Ave 10", "recipientName": "Postman Tester", "recipientPhone": "+77010000000", "isDefault": {d}}}'
users = folder("Users", [
    req("Get my profile", "GET", "api/users/me", [
        status(200),
        check("Is me, with no secrets", 'json.email === pm.environment.get("userEmail") && json.role === "CUSTOMER" && json.password === undefined'),
    ]),
    req("Update my profile", "PUT", "api/users/me", [
        status(200), check("Name changed", 'json.firstName === "Updated"'),
    ], body='{"firstName": "Updated", "lastName": "Tester", "phone": "+77010000009"}'),
    req("Update profile - validation failure", "PUT", "api/users/me",
        fail(400) + [check("Names firstName", 'json.details.firstName')], body='{"firstName": "", "lastName": "Tester"}'),
    req("Add address (becomes the default)", "POST", "api/users/me/addresses", [
        status(201),
        'pm.test("Has a Location header", function () { pm.expect(pm.response.headers.get("Location")).to.exist; });',
        check("First address is the default", 'json.isDefault === true'),
        setvar("addressId", "pm.response.json().id"),
    ], body=ADDR.format(t="Home", c="Almaty", d="false")),
    req("Add a second address as default", "POST", "api/users/me/addresses", [
        status(201), check("New default", 'json.isDefault === true'), setvar("addressId2", "pm.response.json().id"),
    ], body=ADDR.format(t="Work", c="Astana", d="true")),
    req("Add address - validation failure", "POST", "api/users/me/addresses",
        fail(400) + [check("Names the missing fields", 'json.details.country && json.details.street')], body='{"city": "Almaty"}'),
    req("List my addresses (paged)", "GET", "api/users/me/addresses", PAGE() + [
        status(200), check("Two addresses, default first", 'json.totalElements === 2 && json.content[0].isDefault === true'),
    ]),
    req("Get one address", "GET", "api/users/me/addresses/{{addressId}}", [status(200), check("Same id", 'json.id === Number(pm.environment.get("addressId"))')]),
    req("Update an address", "PUT", "api/users/me/addresses/{{addressId}}", [
        status(200), check("City changed", 'json.city === "Shymkent"'),
    ], body=ADDR.format(t="Home", c="Shymkent", d="false")),
    req("Delete the second address", "DELETE", "api/users/me/addresses/{{addressId2}}", [
        status(204), check_text("No body on 204", 'text === ""'),
    ]),
    req("Get a deleted address -> 404", "GET", "api/users/me/addresses/{{addressId2}}", fail(404)),
    req("Change password via /users/me/password", "PUT", "api/users/me/password", [
        status(204), setvar("userPassword", '"Fin4lStrongPass"'),
    ], body='{"currentPassword": "{{userPassword}}", "newPassword": "Fin4lStrongPass"}'),
    req("Log in again after the change", "POST", "api/auth/login", [
        status(200), setvar("accessToken", "pm.response.json().accessToken"), setvar("refreshToken", "pm.response.json().refreshToken"),
    ], auth=None, body='{"email": "{{userEmail}}", "password": "{{userPassword}}"}'),
])

# ========================================================================== PRODUCTS
products = folder("Products", [
    req("List products (paged)", "GET", "api/products", PAGE() + [
        status(200),
        check("Seeded catalogue is there", 'json.totalElements >= 20'),
        check("Summaries hide internals", 'json.content[0].reservedQuantity === undefined && json.content[0].stockQuantity === undefined'),
        setvar("productId", "pm.response.json().content[0].id"),
        setvar("productId2", "pm.response.json().content[1].id"),
        setvar("productId3", "pm.response.json().content[2].id"),
    ], auth=None, query={"page": 0, "size": 20}),
    req("List with filters and sorting", "GET", "api/products", PAGE() + [
        status(200), check("At most 5 results, all in stock", 'json.content.length <= 5 && json.content.every(function (p) { return p.inStock; })'),
    ], auth=None, query={"minPrice": 100, "maxPrice": 1500, "inStock": "true", "sort": "price,asc", "size": 5}),
    req("Search", "GET", "api/products/search", PAGE() + [
        status(200), check("Every hit matches", 'json.content.length > 0 && json.content.every(function (p) { return /galaxy/i.test(p.name); })'),
    ], auth=None, query={"q": "galaxy"}),
    req("Featured", "GET", "api/products/featured", PAGE() + [status(200)], auth=None),
    req("New arrivals", "GET", "api/products/new-arrivals", PAGE() + [status(200)], auth=None),
    req("Best sellers", "GET", "api/products/best-sellers", PAGE() + [status(200)], auth=None),
    req("Get one product", "GET", "api/products/{{productId}}", [
        status(200),
        check("Has price, category, brand and images", 'json.currentPrice > 0 && json.categoryName && json.brandName && Array.isArray(json.images)'),
        setvar("productName", "pm.response.json().name"),
    ], auth=None),
    req("Product variants", "GET", "api/products/{{productId}}/variants", [status(200), check("Is an array", 'Array.isArray(json)')], auth=None),
    req("Related products", "GET", "api/products/{{productId}}/related", [
        status(200), check("Does not include the product itself", 'json.every(function (p) { return p.id !== Number(pm.environment.get("productId")); })'),
    ], auth=None),
    req("FAIL not found: unknown product", "GET", "api/products/999999", fail(404), auth=None),
    req("FAIL bad request: sorting by a forbidden field", "GET", "api/products", fail(400), auth=None, query={"sort": "password,asc"}),
    req("FAIL bad request: id that is not a number", "GET", "api/products/abc", fail(400), auth=None),
])

# ========================================================================== CATEGORIES + BRANDS
categories = folder("Categories", [
    req("Category tree", "GET", "api/categories", [
        status(200), check("Roots with nested children", 'Array.isArray(json) && json.length > 0 && Array.isArray(json[0].children)'),
        setvar("categoryId", "pm.response.json()[0].id"), setvar("categorySlug", "pm.response.json()[0].slug"),
    ], auth=None),
    req("Get category by id", "GET", "api/categories/{{categoryId}}", [status(200), check("Same category", 'json.id === Number(pm.environment.get("categoryId"))')], auth=None),
    req("Get category by slug", "GET", "api/categories/{{categorySlug}}", [status(200), check("Same slug", 'json.slug === pm.environment.get("categorySlug")')], auth=None),
    req("Products in a category (includes subcategories)", "GET", "api/categories/{{categoryId}}/products", PAGE() + [status(200)], auth=None),
    req("FAIL not found: unknown category", "GET", "api/categories/999999", fail(404), auth=None),
])
brands = folder("Brands", [
    req("List brands (paged)", "GET", "api/brands", PAGE() + [
        status(200), check("Three seeded brands, alphabetical", 'json.totalElements >= 3 && json.content[0].name <= json.content[1].name'),
        setvar("brandId", "pm.response.json().content[0].id"),
    ], auth=None),
    req("Get one brand", "GET", "api/brands/{{brandId}}", [status(200), check("Same brand", 'json.id === Number(pm.environment.get("brandId"))')], auth=None),
    req("Products of a brand", "GET", "api/brands/{{brandId}}/products", PAGE() + [status(200)], auth=None),
    req("FAIL not found: unknown brand", "GET", "api/brands/999999", fail(404), auth=None),
])

# ========================================================================== REVIEWS
reviews = folder("Reviews", [
    req("List reviews of a product (paged)", "GET", "api/products/{{productId}}/reviews", PAGE() + [status(200)], auth=None),
    req("Review summary", "GET", "api/products/{{productId}}/reviews/summary", [
        status(200), check("Has every star 1 to 5", 'Object.keys(json.distribution).length === 5 && typeof json.averageRating === "number"'),
    ], auth=None),
    req("FAIL unauthorised: posting without a token", "POST", "api/products/{{productId}}/reviews", fail(401), auth=None, body='{"rating": 5}'),
    req("Post a review", "POST", "api/products/{{productId}}/reviews", [
        status(201),
        'pm.test("Has a Location header", function () { pm.expect(pm.response.headers.get("Location")).to.exist; });',
        check("Not verified (never bought), approved, author shown as first name + initial",
              'json.isVerifiedPurchase === false && json.status === "APPROVED" && /^[A-Za-z]+ [A-Z]\\.$/.test(json.authorName)'),
        setvar("reviewId", "pm.response.json().id"),
    ], body='{"rating": 4, "title": "Solid", "comment": "Works as described", "isVerifiedPurchase": true, "status": "REJECTED"}',
        desc="The body deliberately tries to set isVerifiedPurchase and status: the server ignores both."),
    req("The product rating now reflects it", "GET", "api/products/{{productId}}", [
        status(200), check("Review counted", 'json.reviewCount >= 1 && json.averageRating > 0'),
    ], auth=None),
    req("FAIL conflict: a second review of the same product", "POST", "api/products/{{productId}}/reviews", fail(409), body='{"rating": 5}'),
    req("FAIL validation: rating out of range", "POST", "api/products/{{productId2}}/reviews",
        fail(400) + [check("Names the rating field", 'json.details.rating')], body='{"rating": 9}'),
    req("Edit my review", "PUT", "api/products/{{productId}}/reviews/{{reviewId}}", [
        status(200), check("Rating changed", 'json.rating === 5'),
    ], body='{"rating": 5, "title": "Even better", "comment": "Updated after a week"}'),
    req("FAIL not found: editing a review that does not exist", "PUT", "api/products/{{productId}}/reviews/999999", fail(404), body='{"rating": 5}'),
    req("Delete my review", "DELETE", "api/products/{{productId}}/reviews/{{reviewId}}", [
        status(204), check_text("No body on 204", 'text === ""'),
    ]),
])

# ========================================================================== DELIVERY + PROMO
delivery = folder("Delivery", [
    req("Delivery methods", "GET", "api/delivery/methods", [
        status(200), check("Four options", 'Array.isArray(json) && json.length === 4'),
    ], auth=None),
    req("Calculate: below the free threshold", "POST", "api/delivery/calculate", [
        status(200), check("Charged", 'json.cost === 5 && json.free === false'),
    ], auth=None, body='{"deliveryMethod": "COURIER", "subtotal": 50}'),
    req("Calculate: free above the threshold", "POST", "api/delivery/calculate", [
        status(200), check("Free", 'json.cost === 0 && json.free === true && json.estimatedFrom && json.estimatedTo'),
    ], auth=None, body='{"deliveryMethod": "COURIER", "subtotal": 150}'),
    req("FAIL validation: unknown method and negative subtotal", "POST", "api/delivery/calculate", fail(400), auth=None,
        body='{"deliveryMethod": "TELEPORT", "subtotal": -5}'),
])
promo = folder("Promo", [
    req("Validate a code against an amount", "POST", "api/promo/validate", [
        status(200), check("10% of 200", 'json.code === "WELCOME10" && json.discountAmount === 20'),
    ], body='{"code": "welcome10", "orderAmount": 200}'),
    req("FAIL: expired code", "POST", "api/promo/validate", fail(400) + [check("Says why", 'json.message === "Promo code has expired"')],
        body='{"code": "OLDSALE", "orderAmount": 200}'),
    req("FAIL: below the minimum order amount", "POST", "api/promo/validate", fail(400), body='{"code": "WELCOME10", "orderAmount": 10}'),
    req("FAIL unauthorised: no token", "POST", "api/promo/validate", fail(401), auth=None, body='{"code": "WELCOME10", "orderAmount": 200}'),
])

# ========================================================================== CART + WISHLIST
CART = [
    req("Get my cart (empty)", "GET", "api/cart", [
        status(200), check("Empty", 'Array.isArray(json.items) && json.items.length === 0 && json.total === 0'),
    ]),
    req("Add an item", "POST", "api/cart/items", [
        status(201), check("Two units at the current price", 'json.items.length === 1 && json.items[0].quantity === 2 && json.items[0].unitPrice > 0'),
        setvar("cartItemId", "pm.response.json().items[0].id"),
    ], body='{"productId": {{productId}}, "quantity": 2}'),
    req("FAIL validation: quantity 0", "POST", "api/cart/items", fail(400) + [check("Names quantity", 'json.details.quantity')],
        body='{"productId": {{productId}}, "quantity": 0}'),
    req("FAIL not found: unknown product", "POST", "api/cart/items", fail(404), body='{"productId": 999999, "quantity": 1}'),
    req("FAIL conflict: more than is in stock", "POST", "api/cart/items", fail(409), body='{"productId": {{productId}}, "quantity": 60}',
        desc="The cart already holds 2; 62 is more than the 49 in stock, and nothing is reserved by the refusal."),
    req("Change the quantity", "PUT", "api/cart/items/{{cartItemId}}", [
        status(200), check("One unit", 'json.items[0].quantity === 1'),
    ], body='{"quantity": 1}'),
    req("Add a second product", "POST", "api/cart/items", [
        status(201), check("Two lines", 'json.items.length === 2'),
        setvar("cartItemId2", "pm.response.json().items.find(function (i) { return i.productId === Number(pm.environment.get(\"productId2\")); }).id"),
    ], body='{"productId": {{productId2}}, "quantity": 1}'),
    req("Remove the second product", "DELETE", "api/cart/items/{{cartItemId2}}", [
        status(200), check("One line left", 'json.items.length === 1'),
    ]),
    req("FAIL not found: removing it again", "DELETE", "api/cart/items/{{cartItemId2}}", fail(404)),
    req("Apply a promo code", "POST", "api/cart/apply-promo", [
        status(200), check("Discounted", 'json.appliedPromoCode === "WELCOME10" && json.discount > 0 && json.total < json.subtotal'),
    ], body='{"code": "WELCOME10"}'),
    req("FAIL: apply an expired code", "POST", "api/cart/apply-promo", fail(400), body='{"code": "OLDSALE"}'),
    req("Remove the promo code", "DELETE", "api/cart/promo", [
        status(200), check("No code, no discount", '!json.appliedPromoCode && json.discount === 0'),
    ]),
    req("Guest: add an item without logging in", "POST", "api/cart/items", [
        status(201),
        'pm.test("Server issued a cart session id", function () { pm.expect(pm.response.headers.get("X-Cart-Session")).to.exist; });',
        setvar("guestSession", 'pm.response.headers.get("X-Cart-Session")'),
    ], auth=None, body='{"productId": {{productId}}, "quantity": 1}'),
    req("Guest: read the guest cart", "GET", "api/cart", [
        status(200), check("The guest item is there", 'json.items.length === 1'),
    ], auth=None, headers={"X-Cart-Session": "{{guestSession}}"}),
    req("FAIL unauthorised: merging needs a login", "POST", "api/cart/merge", fail(401), auth=None, body='{"sessionId": "{{guestSession}}"}'),
    req("Merge the guest cart into mine", "POST", "api/cart/merge", [
        status(200), check("Same product added up", 'json.items.length === 1 && json.items[0].quantity === 2'),
    ], body='{"sessionId": "{{guestSession}}"}'),
    req("FAIL validation: merging a malformed session id", "POST", "api/cart/merge", fail(400), body='{"sessionId": "nope"}'),
]
cart = folder("Cart", CART)

wishlist = folder("Wishlist", [
    req("FAIL unauthorised: wishlist needs a login", "GET", "api/wishlist", fail(401), auth=None),
    req("Add to wishlist", "POST", "api/wishlist/{{productId3}}", [
        status(201), check("Price remembered", 'json.priceAtAdd > 0 && json.priceDropped === false'),
    ], body='{"notifyOnPriceDrop": true}'),
    req("FAIL conflict: adding it twice", "POST", "api/wishlist/{{productId3}}", fail(409)),
    req("List my wishlist (paged)", "GET", "api/wishlist", PAGE() + [status(200), check("One entry", 'json.totalElements === 1')]),
    req("Remove from wishlist", "DELETE", "api/wishlist/{{productId3}}", [status(204), check_text("No body on 204", 'text === ""')]),
    req("FAIL not found: removing it again", "DELETE", "api/wishlist/{{productId3}}", fail(404)),
    req("Add again, then move to the cart", "POST", "api/wishlist/{{productId3}}", [status(201)]),
    req("Move to cart", "POST", "api/wishlist/{{productId3}}/move-to-cart", [
        status(200), check("Now in the cart", 'json.items.some(function (i) { return i.productId === Number(pm.environment.get("productId3")); })'),
    ]),
])

# ========================================================================== ORDERS + PAYMENTS
CARD = '{{"orderId": {{{{orderId}}}}, "paymentMethod": "CARD", "cardNumber": "{n}", "expiry": "12/45", "cvv": "123"}}'
orders = folder("Orders", [
    req("Create an order from the cart", "POST", "api/orders", [
        status(201),
        'pm.test("Has a Location header", function () { pm.expect(pm.response.headers.get("Location")).to.exist; });',
        check("PENDING, with items and a number", 'json.status === "PENDING" && json.items.length >= 1 && /^ORD-\\d{4}-\\d+$/.test(json.orderNumber)'),
        check("Total follows the formula: subtotal - discount + shipping",
              'Math.abs(json.totalAmount - (json.subtotal - json.discount + json.shippingCost)) < 0.005'),
        check("Address was copied into the order", 'json.shippingAddress.city === "Shymkent"'),
        setvar("orderId", "pm.response.json().id"), setvar("orderNumber", "pm.response.json().orderNumber"),
    ], body='{"addressId": {{addressId}}, "deliveryMethod": "COURIER", "customerNote": "Postman order"}',
        desc="Reserves stock, empties the cart and returns the order. The client never sends prices or totals."),
    req("FAIL: the cart is empty now", "POST", "api/orders", fail(400) + [check("Says so", 'json.message === "Your cart is empty"')],
        body='{"addressId": {{addressId}}, "deliveryMethod": "COURIER"}'),
    req("List my orders (paged)", "GET", "api/orders", PAGE() + [status(200), check("My order is first", 'json.content[0].id === Number(pm.environment.get("orderId"))')]),
    req("Get an order by id", "GET", "api/orders/{{orderId}}", [status(200), check("Same order", 'json.id === Number(pm.environment.get("orderId"))')]),
    req("Get an order by its number", "GET", "api/orders/{{orderNumber}}", [status(200), check("Same order", 'json.orderNumber === pm.environment.get("orderNumber")')]),
    req("FAIL not found: unknown order", "GET", "api/orders/999999", fail(404)),
    req("FAIL forbidden: another customer's order looks like it does not exist", "GET", "api/orders/ORD-2026-0001", fail(404),
        desc="ORD-2026-0001 belongs to the seeded demo customer. Ownership is enforced: you get the same 404 as for a missing order."),
    req("FAIL unauthorised: orders need a login", "GET", "api/orders", fail(401), auth=None),
    req("Payment methods", "GET", "api/payments/methods", [
        status(200), check("Card, PayPal, cash on delivery", 'json.map(function (m) { return m.method; }).sort().join() === "CARD,CASH_ON_DELIVERY,PAYPAL"'),
    ], auth=None),
    req("Payment status before paying", "GET", "api/payments/{{orderId}}/status", [
        status(200), check("Not attempted yet", 'json.orderStatus === "PENDING" && !json.paymentStatus'),
    ]),
    req("FAIL validation: card number fails the Luhn check", "POST", "api/payments/process", fail(400) + [check("Says so", 'json.message === "Card number is not valid"')],
        body=CARD.format(n="4111111111111112")),
    req("Pay: insufficient funds (4000000000000002)", "POST", "api/payments/process", fail(402) + [
        check("Says insufficient funds", '/insufficient funds/i.test(json.message)')], body=CARD.format(n="4000000000000002")),
    req("Order stays CONFIRMED, never PAID", "GET", "api/payments/{{orderId}}/status", [
        status(200), check("CONFIRMED with a FAILED payment and only 4 digits kept",
                           'json.orderStatus === "CONFIRMED" && json.paymentStatus === "FAILED" && json.payment.cardLastFourDigits === "0002"'),
    ]),
    req("Pay: network error (4000000000000069)", "POST", "api/payments/process", fail(402) + [check("Says network", '/network/i.test(json.message)')],
        body=CARD.format(n="4000000000000069")),
    req("Pay: suspected fraud (4000000000000077)", "POST", "api/payments/process", fail(402) + [check("Says fraud", '/fraud/i.test(json.message)')],
        body=CARD.format(n="4000000000000077")),
    req("Pay: the always-succeeds card (4111111111111111)", "POST", "api/payments/process", [
        status(200),
        check("COMPLETED, last four digits only", 'json.status === "COMPLETED" && json.cardLastFourDigits === "1111" && /^TXN-/.test(json.transactionId)'),
        check_text("The full card number is not in the response", '!/4111111111111111/.test(text)'),
    ], body=CARD.format(n="4111111111111111"),
        desc="The simulated bank pauses 1 to 3 seconds before answering."),
    req("Payment status after paying", "GET", "api/payments/{{orderId}}/status", [
        status(200), check("PAID", 'json.orderStatus === "PAID" && json.paymentStatus === "COMPLETED"'),
    ]),
    req("FAIL conflict: paying twice", "POST", "api/payments/process", fail(409), body=CARD.format(n="4111111111111111")),
    req("FAIL conflict: a paid order cannot be cancelled by the customer", "PUT", "api/orders/{{orderId}}/cancel", fail(409)),
    req("Order again (re-adds the items to the cart)", "POST", "api/orders/{{orderId}}/reorder", [
        status(200), check("Items are back in the cart", 'json.cart.items.length >= 1 && Array.isArray(json.skipped)'),
    ]),
    req("Create a second order", "POST", "api/orders", [
        status(201), setvar("orderId2", "pm.response.json().id"),
    ], body='{"addressId": {{addressId}}, "deliveryMethod": "EXPRESS"}'),
    req("Cancel the second order", "PUT", "api/orders/{{orderId2}}/cancel", [
        status(200), check("CANCELLED", 'json.status === "CANCELLED"'),
    ]),
    req("FAIL conflict: cancelling it again", "PUT", "api/orders/{{orderId2}}/cancel", fail(409)),
    req("FAIL validation: unknown delivery method", "POST", "api/orders", fail(400), body='{"addressId": {{addressId}}, "deliveryMethod": "TELEPORT"}'),
])

# ========================================================================== ADMIN
ts = '"PM" + Date.now()'
admin = folder("Admin", [
    folder("Access control", [
        req("FAIL forbidden: a CUSTOMER token on an admin endpoint", "GET", "api/admin/orders", fail(403)),
        req("FAIL forbidden: a CUSTOMER cannot create products", "POST", "api/admin/products", fail(403), body='{}'),
        req("FAIL unauthorised: no token on an admin endpoint", "GET", "api/admin/orders", fail(401), auth=None),
    ]),
    folder("Orders", [
        req("List orders (paged, filter by number)", "GET", "api/admin/orders", PAGE() + [
            status(200), check("Finds the order and shows the customer", 'json.totalElements === 1 && json.content[0].customerEmail === pm.environment.get("userEmail")'),
        ], auth="admin", query={"search": "{{orderNumber}}"}),
        req("Filter orders by status", "GET", "api/admin/orders", PAGE() + [
            status(200), check("All SHIPPED", 'json.content.every(function (o) { return o.order.status === "SHIPPED"; })'),
        ], auth="admin", query={"status": "SHIPPED"}),
        req("Get one order (staff view)", "GET", "api/admin/orders/{{orderId}}", [
            status(200), check("PAID", 'json.order.status === "PAID"'),
        ], auth="admin"),
        req("FAIL conflict: an illegal move (PAID -> DELIVERED)", "PUT", "api/admin/orders/{{orderId}}/status", fail(409),
            auth="admin", body='{"status": "DELIVERED"}'),
        req("FAIL validation: a status that does not exist", "PUT", "api/admin/orders/{{orderId}}/status", fail(400), auth="admin", body='{"status": "TELEPORTED"}'),
        req("Status: PAID -> PROCESSING", "PUT", "api/admin/orders/{{orderId}}/status", [status(200), check("PROCESSING", 'json.order.status === "PROCESSING"')],
            auth="admin", body='{"status": "PROCESSING"}'),
        req("Status: PROCESSING -> SHIPPED (stock leaves the warehouse)", "PUT", "api/admin/orders/{{orderId}}/status", [
            status(200), check("SHIPPED with a timestamp", 'json.order.status === "SHIPPED" && json.order.shippedAt'),
        ], auth="admin", body='{"status": "SHIPPED"}'),
        req("Set tracking", "PUT", "api/admin/orders/{{orderId}}/tracking", [
            status(200), check("Saved", 'json.order.deliveryTrackingNumber === "KZ-POSTMAN-1"'),
        ], auth="admin", body='{"trackingNumber": "KZ-POSTMAN-1", "estimatedDeliveryDate": "2030-01-15"}'),
        req("FAIL validation: tracking needs a number", "PUT", "api/admin/orders/{{orderId}}/tracking", fail(400), auth="admin", body='{}'),
        req("Set a staff note", "PUT", "api/admin/orders/{{orderId}}/note", [
            status(200), check("Saved", 'json.adminNote === "Fragile"'),
        ], auth="admin", body='{"adminNote": "Fragile"}'),
        req("Status: SHIPPED -> IN_TRANSIT", "PUT", "api/admin/orders/{{orderId}}/status", [status(200)], auth="admin", body='{"status": "IN_TRANSIT"}'),
        req("Status: IN_TRANSIT -> DELIVERED", "PUT", "api/admin/orders/{{orderId}}/status", [
            status(200), check("DELIVERED", 'json.order.status === "DELIVERED" && json.order.deliveredAt'),
        ], auth="admin", body='{"status": "DELIVERED"}'),
        req("FAIL conflict: a delivered order can only be refunded through a return", "PUT", "api/admin/orders/{{orderId}}/status", fail(409),
            auth="admin", body='{"status": "REFUNDED"}'),
        req("The customer sees DELIVERED and the tracking number, but not the staff note", "GET", "api/orders/{{orderId}}", [
            status(200), check("Visible to the customer", 'json.status === "DELIVERED" && json.deliveryTrackingNumber === "KZ-POSTMAN-1"'),
            check_text("The staff note is not in the customer's response", '!/Fragile|adminNote/.test(text)'),
        ]),
        req("FAIL not found: unknown order", "GET", "api/admin/orders/999999", fail(404), auth="admin"),
    ]),
    folder("Catalogue", [
        req("Create a category", "POST", "api/admin/categories", [
            status(201), 'pm.test("Has a Location header", function () { pm.expect(pm.response.headers.get("Location")).to.exist; });',
            setvar("adminCategoryId", "pm.response.json().id"),
        ], auth="admin", pre=f'pm.environment.set("uniq", {ts});', body='{"name": "Postman Category {{uniq}}"}'),
        req("Update a category", "PUT", "api/admin/categories/{{adminCategoryId}}", [status(200), check("Renamed", '/Renamed/.test(json.name)')],
            auth="admin", body='{"name": "Renamed Category {{uniq}}", "sortOrder": 7}'),
        req("Reorder categories", "PUT", "api/admin/categories/reorder", [status(204), check_text("No body on 204", 'text === ""')],
            auth="admin", body='{"items": [{"id": {{adminCategoryId}}, "sortOrder": 9}]}'),
        req("FAIL validation: category without a name", "POST", "api/admin/categories", fail(400) + [check("Names name", 'json.details.name')], auth="admin", body='{"name": ""}'),
        req("Create a brand", "POST", "api/admin/brands", [status(201), setvar("adminBrandId", "pm.response.json().id")],
            auth="admin", body='{"name": "Postman Brand {{uniq}}", "country": "Kazakhstan"}'),
        req("Update a brand", "PUT", "api/admin/brands/{{adminBrandId}}", [status(200), check("Renamed", '/Renamed/.test(json.name)')],
            auth="admin", body='{"name": "Renamed Brand {{uniq}}"}'),
        req("Create a product", "POST", "api/admin/products", [
            status(201), 'pm.test("Has a Location header", function () { pm.expect(pm.response.headers.get("Location")).to.exist; });',
            check("Slug generated, category and brand attached", 'json.slug && json.categoryName && json.brandName'),
            setvar("adminProductId", "pm.response.json().id"),
        ], auth="admin", body='{"name": "Postman Gadget {{uniq}}", "sku": "{{uniq}}", "basePrice": 99.90, "stockQuantity": 10, "categoryId": {{adminCategoryId}}, "brandId": {{adminBrandId}}}'),
        req("FAIL validation: a product with bad fields", "POST", "api/admin/products", fail(400) + [check("Names the fields", 'json.details.name && json.details.basePrice && json.details.sku && json.details.categoryId')],
            auth="admin", body='{"name": "A", "basePrice": -5}'),
        req("FAIL conflict: duplicate SKU", "POST", "api/admin/products", fail(409), auth="admin",
            body='{"name": "Duplicate", "sku": "{{uniq}}", "basePrice": 10, "stockQuantity": 1, "categoryId": {{adminCategoryId}}}'),
        req("FAIL not found: product in an unknown category", "POST", "api/admin/products", fail(404), auth="admin",
            body='{"name": "Ghost", "sku": "GHOST{{uniq}}", "basePrice": 10, "stockQuantity": 1, "categoryId": 999999}'),
        req("Update a product", "PUT", "api/admin/products/{{adminProductId}}", [status(200), check("Changed", 'json.basePrice === 109.9')],
            auth="admin", body='{"basePrice": 109.90}'),
        req("Change stock", "PATCH", "api/admin/products/{{adminProductId}}/stock", [status(200), check("Updated", 'json.availableQuantity === 50')],
            auth="admin", body='{"stockQuantity": 50}'),
        req("Deactivate a product", "PATCH", "api/admin/products/{{adminProductId}}/deactivate", [status(200), check("Inactive", 'json.isActive === false')], auth="admin"),
        req("A deactivated product is hidden from the public", "GET", "api/products/{{adminProductId}}", fail(404), auth=None),
        req("Activate it again", "PATCH", "api/admin/products/{{adminProductId}}/activate", [status(200), check("Active", 'json.isActive === true')], auth="admin"),
        req("Add a variant", "POST", "api/admin/products/{{adminProductId}}/variants", [status(201), setvar("adminVariantId", "pm.response.json().id")],
            auth="admin", body='{"sku": "V{{uniq}}", "name": "Large", "price": 119.90, "stockQuantity": 5}'),
        req("Update the variant", "PUT", "api/admin/products/{{adminProductId}}/variants/{{adminVariantId}}", [status(200), check("Renamed", 'json.name === "XL"')],
            auth="admin", body='{"sku": "V{{uniq}}", "name": "XL", "price": 129.90, "stockQuantity": 6}'),
        req("Delete the variant", "DELETE", "api/admin/products/{{adminProductId}}/variants/{{adminVariantId}}", [status(204), check_text("No body on 204", 'text === ""')], auth="admin"),
        req("Delete (soft) the product", "DELETE", "api/admin/products/{{adminProductId}}", [status(204), check_text("No body on 204", 'text === ""')], auth="admin"),
        req("FAIL conflict: a category that still has a product cannot be deleted", "DELETE", "api/admin/categories/{{adminCategoryId}}", fail(409), auth="admin",
            desc="The product was only soft-deleted (hidden), because orders may still reference it. So its category stays in use."),
        req("Create and delete an empty category", "POST", "api/admin/categories", [status(201), setvar("tmpCategoryId", "pm.response.json().id")],
            auth="admin", body='{"name": "Temporary {{uniq}}"}'),
        req("Delete the empty category", "DELETE", "api/admin/categories/{{tmpCategoryId}}", [status(204), check_text("No body on 204", 'text === ""')], auth="admin"),
        req("FAIL not found: deleting it again", "DELETE", "api/admin/categories/{{tmpCategoryId}}", fail(404), auth="admin"),
        req("Create and delete an unused brand", "POST", "api/admin/brands", [status(201), setvar("tmpBrandId", "pm.response.json().id")],
            auth="admin", body='{"name": "Temporary Brand {{uniq}}"}'),
        req("Delete the unused brand", "DELETE", "api/admin/brands/{{tmpBrandId}}", [status(204)], auth="admin"),
        req("FAIL conflict: a brand that still has products cannot be deleted", "DELETE", "api/admin/brands/{{adminBrandId}}", fail(409), auth="admin"),
    ]),
    folder("Promo codes", [
        req("List promo codes (paged)", "GET", "api/admin/promo-codes", PAGE() + [status(200), check("At least the three seeded codes", 'json.totalElements >= 3')], auth="admin"),
        req("Create a promo code", "POST", "api/admin/promo-codes", [
            status(201), 'pm.test("Has a Location header", function () { pm.expect(pm.response.headers.get("Location")).to.exist; });',
            check("Stored in upper case", 'json.code === pm.environment.get("promoCode")'), setvar("adminPromoId", "pm.response.json().id"),
        ], auth="admin", pre=['pm.environment.set("promoCode", "PM" + Date.now());'],
            body='{"code": "{{promoCode}}", "description": "Postman", "discountType": "PERCENTAGE", "discountValue": 12, "minOrderAmount": 50}'),
        req("FAIL conflict: duplicate code", "POST", "api/admin/promo-codes", fail(409), auth="admin",
            body='{"code": "{{promoCode}}", "discountType": "PERCENTAGE", "discountValue": 5}'),
        req("FAIL validation: a percentage over 100", "POST", "api/admin/promo-codes", fail(400), auth="admin",
            body='{"code": "TOOBIG{{uniq}}", "discountType": "PERCENTAGE", "discountValue": 150}'),
        req("FAIL validation: missing fields", "POST", "api/admin/promo-codes", fail(400) + [check("Names them", 'json.details.code && json.details.discountType')], auth="admin", body='{}'),
        req("Update a promo code", "PUT", "api/admin/promo-codes/{{adminPromoId}}", [status(200), check("Changed", 'json.discountValue === 15')],
            auth="admin", body='{"code": "{{promoCode}}", "discountType": "PERCENTAGE", "discountValue": 15, "minOrderAmount": 50}'),
        req("Delete a promo code", "DELETE", "api/admin/promo-codes/{{adminPromoId}}", [status(204), check_text("No body on 204", 'text === ""')], auth="admin"),
        req("FAIL not found: updating a deleted code", "PUT", "api/admin/promo-codes/{{adminPromoId}}", fail(404), auth="admin",
            body='{"code": "{{promoCode}}", "discountType": "PERCENTAGE", "discountValue": 15}'),
    ]),
], desc="Uses adminToken (the seeded demo admin). Run it after Orders: it moves that order through its statuses.")

collection = {
    "info": {
        "_postman_id": sid("collection"),
        "name": "Electronics Store API",
        "description": "CIT4040 course project. Import this collection and the environment, then run the **Auth** folder first, "
                       "then any other folder (or run the whole collection in order). Only `baseUrl` may need editing.\n\n"
                       "Every request has a test asserting its status code. Requests named `FAIL ...` are deliberate failures "
                       "(validation, not found, forbidden) and assert the expected error status and the standard error body.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": [auth, users, products, categories, brands, reviews, delivery, cart, wishlist, promo, orders, admin],
}
(OUT / "electronics-store.postman_collection.json").write_text(json.dumps(collection, indent=2) + "\n")

env_vars = ["baseUrl", "accessToken", "refreshToken", "adminToken", "userEmail", "userPassword", "oldRefreshToken", "productId", "productId2",
            "productId3", "productName", "categoryId", "categorySlug", "brandId", "reviewId", "addressId", "addressId2", "cartItemId",
            "cartItemId2", "guestSession", "orderId", "orderId2", "orderNumber", "uniq", "adminCategoryId", "adminBrandId", "adminProductId",
            "adminVariantId", "tmpCategoryId", "tmpBrandId", "promoCode", "adminPromoId"]
environment = {
    "id": sid("environment"),
    "name": "Electronics Store (local)",
    "values": [{"key": k, "value": "http://localhost:8080" if k == "baseUrl" else "", "type": "default", "enabled": True} for k in env_vars],
    "_postman_variable_scope": "environment",
}
(OUT / "electronics-store.postman_environment.json").write_text(json.dumps(environment, indent=2) + "\n")

count = sum(1 for _ in re.finditer(r'"method":', json.dumps(collection)))
print(f"wrote collection ({count} requests) and environment ({len(env_vars)} variables)")
