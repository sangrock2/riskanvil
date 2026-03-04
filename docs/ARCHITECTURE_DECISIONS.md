# Architecture Decision Records (ADR)

Key architectural decisions for Stock-AI platform with rationale, alternatives, and consequences.

---

## Table of Contents

1. [ADR-001: Microservices Architecture](#adr-001-microservices-architecture)
2. [ADR-002: JWT-Based Authentication](#adr-002-jwt-based-authentication)
3. [ADR-003: React Query for State Management](#adr-003-react-query-for-state-management)
4. [ADR-004: Spring Boot 4.0 with Java 21](#adr-004-spring-boot-40-with-java-21)
5. [ADR-005: FastAPI for AI Service](#adr-005-fastapi-for-ai-service)
6. [ADR-006: MySQL as Primary Database](#adr-006-mysql-as-primary-database)
7. [ADR-007: Redis for Caching Layer](#adr-007-redis-for-caching-layer)
8. [ADR-008: Server-Sent Events (SSE) for Streaming](#adr-008-server-sent-events-sse-for-streaming)
9. [ADR-009: ChromaDB for RAG System](#adr-009-chromadb-for-rag-system)
10. [ADR-010: Resilience4j Circuit Breaker](#adr-010-resilience4j-circuit-breaker)
11. [ADR-011: Flyway for Database Migrations](#adr-011-flyway-for-database-migrations)
12. [ADR-012: NumPy Vectorization for Monte Carlo](#adr-012-numpy-vectorization-for-monte-carlo)

---

## ADR Format

Each ADR follows this structure:
- **Status**: Accepted, Proposed, Superseded, Deprecated
- **Context**: Problem description and constraints
- **Decision**: What was decided
- **Alternatives**: Options considered but rejected
- **Consequences**: Trade-offs and implications
- **Related**: Links to related ADRs

---

## ADR-001: Microservices Architecture

**Status**: Accepted (2024-01)

### Context

Need to build a stock analysis platform with distinct concerns:
- **User-facing features**: Authentication, portfolio management, watchlist
- **AI/ML workloads**: Claude API integration, technical analysis, Monte Carlo simulations
- **Real-time data**: Market quotes, WebSocket streams

**Requirements**:
- Scale AI service independently (CPU-intensive)
- Different tech stacks for different domains (Java/Python)
- Allow parallel development by multiple teams
- Deploy services independently

### Decision

Split into **3 microservices**:

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│  Frontend   │─────▶│   Backend   │─────▶│ AI Service  │
│  (React)    │      │ (Spring Boot)│      │  (FastAPI)  │
└─────────────┘      └──────┬──────┘      └─────────────┘
                            │
                            ▼
                     ┌─────────────┐
                     │ MySQL+Redis │
                     └─────────────┘
```

**Service Boundaries**:
1. **Frontend**: User interface, routing, client-side state
2. **Backend**: Business logic, database operations, authentication, API gateway
3. **AI Service**: Claude API, yfinance, technical indicators, simulations

### Alternatives

**1. Monolithic Architecture**:
- **Pros**: Simpler deployment, no network overhead, easier transactions
- **Cons**: Can't scale AI independently, single tech stack, harder to develop in parallel
- **Rejected**: AI workloads require different scaling characteristics than CRUD operations

**2. More Fine-Grained Microservices** (5+ services):
- **Pros**: Maximum isolation, very granular scaling
- **Cons**: Increased complexity, distributed transactions, more network calls
- **Rejected**: Overhead not justified for small team (3-5 developers)

### Consequences

**Positive**:
- ✅ **Independent scaling**: AI service uses 4 CPU cores, Backend uses 2 cores
- ✅ **Technology flexibility**: Java for backend (mature ecosystem), Python for AI (ML libraries)
- ✅ **Fault isolation**: AI service crash doesn't bring down portfolio management
- ✅ **Parallel development**: Backend team and AI team can work independently

**Negative**:
- ❌ **Network latency**: Frontend → Backend → AI adds 10-50ms per request
- ❌ **Distributed debugging**: Need distributed tracing (X-Request-Id header)
- ❌ **Data consistency**: No distributed transactions (mitigated with eventual consistency)
- ❌ **Deployment complexity**: 3 services to deploy, orchestrate, monitor

**Mitigations**:
- Request ID propagation for distributed tracing
- Circuit breaker (Resilience4j) for fault tolerance
- Caching layer (Redis) to reduce AI service calls
- Docker Compose for local development simplicity

---

## ADR-002: JWT-Based Authentication

**Status**: Accepted (2024-01)

### Context

Need stateless authentication that:
- Works across microservices (Frontend ↔ Backend ↔ AI Service)
- Supports mobile apps in future
- Scales horizontally without session replication
- Enables fine-grained access control (RBAC)

**Security Requirements**:
- Protect sensitive endpoints (portfolio, watchlist)
- Support token refresh without re-login
- Prevent token replay attacks
- Revoke tokens on logout

### Decision

**JWT (JSON Web Token) with Refresh Token Pattern**:

```
┌─────────┐                    ┌─────────┐
│ Client  │                    │ Backend │
└────┬────┘                    └────┬────┘
     │ POST /auth/login            │
     │ {username, password}        │
     │────────────────────────────▶│
     │                             │ Validate credentials
     │                             │ Generate tokens
     │◀────────────────────────────│
     │ {token, refreshToken, user} │
     │                             │
     │ GET /api/watchlist          │
     │ Authorization: Bearer {token}│
     │────────────────────────────▶│
     │◀────────────────────────────│ Verify JWT
     │ {items: [...]}              │
     │                             │
     │ (15 min later, token expired)│
     │                             │
     │ POST /auth/refresh          │
     │ Authorization: Bearer {refreshToken}│
     │────────────────────────────▶│
     │◀────────────────────────────│ Issue new tokens
     │ {token, refreshToken}       │
```

**Token Specifications**:
- **Access Token**: 15 min TTL, contains `{userId, email, role}`
- **Refresh Token**: 7 days TTL, stored in database, rotated on each refresh
- **Algorithm**: HS256 (HMAC-SHA256)
- **Secret**: 256-bit key from environment variable

### Alternatives

**1. Session-Based Authentication** (Cookie + Session Store):
- **Pros**: Simple revocation, mature pattern, CSRF protection built-in
- **Cons**: Stateful (requires session store), doesn't scale horizontally, complex in microservices
- **Rejected**: Doesn't fit stateless microservices architecture

**2. OAuth 2.0** (with external provider like Auth0):
- **Pros**: Offload auth complexity, social login support, enterprise SSO
- **Cons**: Vendor lock-in, cost ($$$), network dependency, privacy concerns
- **Rejected**: Overkill for MVP, want full control of user data

**3. API Keys**:
- **Pros**: Very simple, long-lived
- **Cons**: Can't identify user, no expiration, hard to revoke, no fine-grained permissions
- **Rejected**: Not suitable for user-facing application

### Consequences

**Positive**:
- ✅ **Stateless**: No session store, scales horizontally
- ✅ **Cross-service**: Backend validates JWT, AI service trusts Backend
- ✅ **Mobile-ready**: Works with React Native, iOS, Android
- ✅ **Fine-grained**: Include `role` in JWT for RBAC
- ✅ **Fast**: Local JWT verification (no DB query on every request)

**Negative**:
- ❌ **Revocation complexity**: Can't immediately invalidate access tokens (15 min window)
- ❌ **Token size**: JWT larger than session cookie (400-600 bytes)
- ❌ **Secret management**: Must protect JWT secret (env variable, not code)
- ❌ **Clock skew**: Requires synchronized clocks (NTP)

**Security Measures**:
- Refresh tokens stored in DB with user_id, expires_at, last_used_at
- Logout invalidates refresh token immediately
- Refresh token rotation (new token issued on each refresh)
- HTTPS only in production (prevent token interception)
- XSS protection (tokens in localStorage, not cookies due to CORS)

---

## ADR-003: React Query for State Management

**Status**: Accepted (2025-12, v0.7.0)

### Context

Original frontend used manual state management:
```javascript
const [data, setData] = useState(null);
const [loading, setLoading] = useState(false);
const [error, setError] = useState(null);

useEffect(() => {
  setLoading(true);
  fetch('/api/watchlist')
    .then(res => res.json())
    .then(data => setData(data))
    .catch(err => setError(err))
    .finally(() => setLoading(false));
}, []);
```

**Problems**:
- Boilerplate repeated in every component (15+ files)
- No caching: Same data fetched multiple times
- Stale data: Manual refresh logic
- Race conditions: Rapid navigation causes overlapping requests
- Optimistic updates: Complex manual implementation

### Decision

**Adopt TanStack Query (React Query)** for all server state:

```javascript
// Custom hook
export function useWatchlist() {
  return useQuery({
    queryKey: ["watchlist"],
    queryFn: () => apiFetch("/api/watchlist"),
    staleTime: 60 * 1000, // 1 minute
  });
}

// Component
const { data, isLoading, error } = useWatchlist();
```

**Data-Type-Specific Stale Times**:
| Data Type | StaleTime | Rationale |
|-----------|-----------|-----------|
| Quote | 30s | Real-time prices change frequently |
| Prices | 2 min | Historical data rarely changes |
| Insights | 5 min | AI analysis expensive to regenerate |
| Fundamentals | 6 hours | Quarterly earnings data |
| Watchlist | 1 min | User-modified data |
| Portfolio | 2 min | Balances recalculated frequently |

### Alternatives

**1. Redux + Redux Toolkit**:
- **Pros**: Mature, predictable state, time-travel debugging
- **Cons**: Boilerplate (actions, reducers, selectors), not optimized for async
- **Rejected**: Overkill for server state, React Query handles async better

**2. Zustand**:
- **Pros**: Lightweight, simple API, no boilerplate
- **Cons**: No built-in caching, refetching, optimistic updates
- **Rejected**: Best for client state, not server state

**3. SWR** (Vercel):
- **Pros**: Similar to React Query, smaller bundle size
- **Cons**: Less feature-rich, smaller community, no devtools
- **Rejected**: React Query more mature and feature-complete

**4. Apollo Client** (GraphQL):
- **Pros**: Excellent caching, GraphQL-first
- **Cons**: Requires GraphQL backend, heavy bundle size (100KB+)
- **Rejected**: Backend uses REST API, not GraphQL

### Consequences

**Positive**:
- ✅ **Less code**: Removed 500+ lines of useState/useEffect boilerplate
- ✅ **Automatic caching**: Reduced API calls by 60%
- ✅ **Stale-while-revalidate**: Show cached data instantly, refetch in background
- ✅ **Optimistic updates**: Instant UI feedback on mutations
- ✅ **Devtools**: Visual query cache inspection
- ✅ **Request deduplication**: Multiple components fetching same data = 1 request

**Negative**:
- ❌ **Bundle size**: +40KB (12KB gzipped) added to frontend
- ❌ **Learning curve**: Team needs to understand query invalidation patterns
- ❌ **Debugging complexity**: Cache state not always obvious

**Migration Effort**:
- 8 pages converted (Dashboard, Watchlist, Portfolio, Analyze, etc.)
- 9 custom hooks created (useWatchlist, usePortfolio, useInsights, etc.)
- 3 days total developer time
- No breaking changes (backward compatible)

---

## ADR-004: Spring Boot 4.0 with Java 21

**Status**: Accepted (2024-01)

### Context

Need mature backend framework for:
- REST API with OpenAPI docs
- Database access (JPA/Hibernate)
- Authentication/Authorization (Spring Security)
- Caching (Spring Cache + Redis)
- Scheduling (background jobs)
- Production-ready (metrics, health checks)

**Requirements**:
- Fast startup (<5s)
- Low memory footprint (<512MB)
- Strong ecosystem (libraries, tutorials)
- Enterprise support

### Decision

**Spring Boot 4.0.0 with Java 21** (latest LTS):
- Spring Web for REST
- Spring Data JPA + Hibernate for database
- Spring Security for authentication
- Spring Cache for Redis integration
- Resilience4j for circuit breaker
- Flyway for database migrations

### Alternatives

**1. Quarkus** (Red Hat):
- **Pros**: Faster startup (1s), lower memory (100MB), native compilation
- **Cons**: Smaller ecosystem, fewer developers know it, less mature
- **Rejected**: Spring Boot more battle-tested, larger talent pool

**2. Micronaut**:
- **Pros**: Fast startup, low memory, GraalVM native
- **Cons**: Smaller community, fewer integrations
- **Rejected**: Similar to Quarkus, Spring Boot preferred for stability

**3. Node.js + Express**:
- **Pros**: JavaScript across frontend/backend, lightweight
- **Cons**: Weak typing (even with TypeScript), less mature for enterprise, poor database tooling
- **Rejected**: Java superior for backend (type safety, performance, libraries)

**4. .NET Core**:
- **Pros**: Excellent performance, mature ecosystem, C# language
- **Cons**: Less popular in finance domain, team unfamiliar
- **Rejected**: Team expertise in Java, Spring Boot

### Consequences

**Positive**:
- ✅ **Mature**: 10+ years of Spring Boot production use
- ✅ **Ecosystem**: 1000+ libraries (Resilience4j, Flyway, TestContainers)
- ✅ **Talent**: Easy to hire Spring Boot developers
- ✅ **Performance**: Java 21 virtual threads improve concurrency
- ✅ **Tooling**: Excellent IDE support (IntelliJ IDEA, Eclipse)

**Negative**:
- ❌ **Startup time**: 5-10s (acceptable for backend, not serverless)
- ❌ **Memory**: 512MB baseline (acceptable, not edge computing)
- ❌ **Verbosity**: More boilerplate than Kotlin/Scala

**Java 21 Features Used**:
- **Virtual Threads**: Handle 10K+ concurrent requests
- **Pattern Matching**: Cleaner instanceof checks
- **Records**: Immutable DTOs without boilerplate
- **Sealed Classes**: Exhaustive switch on InsightType

---

## ADR-005: FastAPI for AI Service

**Status**: Accepted (2024-01)

### Context

AI service needs:
- **ML Libraries**: Access to yfinance, NumPy, Anthropic SDK
- **Fast Development**: Rapid prototyping of analysis algorithms
- **Performance**: Handle CPU-intensive simulations (Monte Carlo)
- **Async**: Concurrent API calls to Claude, yfinance

Python is the dominant language for data science and ML.

### Decision

**FastAPI** (Python 3.12):
- Async support (asyncio, httpx)
- Automatic OpenAPI docs
- Pydantic validation
- High performance (on par with Node.js)

### Alternatives

**1. Flask**:
- **Pros**: Simple, mature, large community
- **Cons**: Synchronous (WSGI), no built-in validation, slower
- **Rejected**: FastAPI async support critical for concurrent API calls

**2. Django + Django REST Framework**:
- **Pros**: Full-featured (ORM, admin, auth), mature
- **Cons**: Heavy (unnecessary features), slower, more boilerplate
- **Rejected**: Overkill for microservice, FastAPI lighter

**3. Node.js + Express**:
- **Pros**: Fast, async, large ecosystem
- **Cons**: Python ML libraries unavailable (yfinance, NumPy)
- **Rejected**: Python required for data science libraries

**4. Go**:
- **Pros**: Extremely fast, built-in concurrency
- **Cons**: ML ecosystem immature, no NumPy equivalent
- **Rejected**: Python ML libraries essential

### Consequences

**Positive**:
- ✅ **ML Ecosystem**: Access to 100K+ PyPI packages (yfinance, pandas, NumPy)
- ✅ **Async**: Handle 100+ concurrent Claude API calls
- ✅ **Fast Development**: Iterate on analysis algorithms quickly
- ✅ **Type Safety**: Pydantic validates requests/responses
- ✅ **Performance**: Vectorized NumPy 100x faster than Python loops

**Negative**:
- ❌ **Deployment**: Requires Python interpreter (not single binary like Go)
- ❌ **Type System**: Weaker than Java (runtime errors possible)
- ❌ **Concurrency**: GIL limits true parallelism (mitigated with NumPy)

**Performance**:
- Monte Carlo (10K simulations): 50ms (vectorized) vs 5s (Python loops)
- Claude API call: 1-3s (network-bound, not CPU)
- yfinance data fetch: 200-500ms (network-bound)

---

## ADR-006: MySQL as Primary Database

**Status**: Accepted (2024-01)

### Context

Need relational database for:
- User accounts, portfolios, watchlist
- Transactional data (analysis runs, backtest history)
- Referential integrity (foreign keys)
- ACID guarantees

**Requirements**:
- Mature, production-tested
- Strong consistency
- Complex queries (JOINs, aggregations)
- Cost-effective (<$100/month for 100K users)

### Decision

**MySQL 8.4** (InnoDB engine):
- Full ACID compliance
- Foreign key constraints
- JSON column type (for flexible schema)
- Window functions (for analytics)

### Alternatives

**1. PostgreSQL**:
- **Pros**: Advanced features (JSONB, full-text search, PostGIS), stronger standards compliance
- **Cons**: Slightly more complex to tune, smaller managed hosting ecosystem
- **Rejected**: Both equally good, team familiar with MySQL

**2. MongoDB**:
- **Pros**: Schema-less, horizontal scaling, high write throughput
- **Cons**: No foreign keys, eventual consistency, complex JOINs
- **Rejected**: Relational data model fits stock portfolios better

**3. Amazon Aurora**:
- **Pros**: MySQL-compatible, auto-scaling, 5x performance
- **Cons**: AWS lock-in, cost ($$$)
- **Rejected**: Want multi-cloud option, MySQL cheaper

**4. SQLite**:
- **Pros**: Serverless, zero config, fast for small data
- **Cons**: No concurrency, no replication, single file
- **Rejected**: Not suitable for production (multiple users)

### Consequences

**Positive**:
- ✅ **Mature**: 25+ years of production use
- ✅ **Reliable**: Battle-tested in finance/banking
- ✅ **Ecosystem**: ORMs (Hibernate, Sequelize), tools (phpMyAdmin, Adminer)
- ✅ **Managed Options**: AWS RDS, Google Cloud SQL, DigitalOcean
- ✅ **Cost**: ~$15/month for 1M rows

**Negative**:
- ❌ **Horizontal Scaling**: Sharding is complex (mitigated: vertical scaling sufficient)
- ❌ **JSON**: JSONB in Postgres slightly faster (not critical path)

**Schema**:
- 19 tables (users, portfolios, watchlist, etc.)
- Foreign keys enforce referential integrity
- Flyway migrations for version control
- Indexes optimized for read-heavy workload

---

## ADR-007: Redis for Caching Layer

**Status**: Accepted (2024-01)

### Context

**Performance Problem**: Market data API calls are slow:
- Quote fetch: 200-500ms (yfinance API)
- Historical prices: 500-1000ms
- Fundamentals: 1-2s

**Usage Patterns**:
- Same stocks queried repeatedly (AAPL, MSFT, GOOGL)
- Dashboard loads 10+ stocks = 10 API calls = 5-10s total
- 80% of requests are for top 20 stocks

**Goal**: Reduce latency to <50ms for cached data.

### Decision

**Redis** as in-memory cache:
- **Key Format**: `quote:AAPL:US`, `prices:AAPL:US:1d`, `fundamentals:AAPL`
- **TTL by Data Type**:
  - Quotes: 30s (real-time)
  - Prices: 2 min (historical)
  - Fundamentals: 6 hours (quarterly earnings)
- **Eviction**: LRU (Least Recently Used)

**Spring Cache Integration**:
```java
@Cacheable(value = "quotes", key = "#ticker + ':' + #market")
public Quote getQuote(String ticker, String market) {
    return yfinanceClient.fetchQuote(ticker, market);
}
```

### Alternatives

**1. Application Memory** (Caffeine, Guava):
- **Pros**: No network, simpler deployment, faster (no serialization)
- **Cons**: Not shared across instances, lost on restart
- **Rejected**: Need shared cache for horizontal scaling

**2. Memcached**:
- **Pros**: Simple, fast, mature
- **Cons**: No persistence, no data structures (only key-value), no pub/sub
- **Rejected**: Redis richer feature set (lists, sets, pub/sub)

**3. Varnish** (HTTP cache):
- **Pros**: Extremely fast, HTTP-aware
- **Cons**: Less flexible (HTTP only), config complexity
- **Rejected**: Need programmatic cache management (eviction, batch updates)

**4. Database Query Cache** (MySQL):
- **Pros**: Built-in, no extra infrastructure
- **Cons**: Slow (still hits DB), invalidation issues
- **Rejected**: Not fast enough (<50ms goal)

### Consequences

**Positive**:
- ✅ **Performance**: Latency reduced from 500ms → 5ms (99% improvement)
- ✅ **Cost**: Reduced API calls by 80% (lower yfinance rate limits)
- ✅ **Scalability**: Shared cache across backend instances
- ✅ **Flexibility**: Support complex data structures (lists, sets, sorted sets)

**Negative**:
- ❌ **Infrastructure**: Extra service to deploy, monitor, maintain
- ❌ **Cost**: $5-20/month for managed Redis
- ❌ **Stale Data**: 30s-6h delay depending on TTL
- ❌ **Memory**: Limited by RAM (1GB = ~1M cache entries)

**Cache Hit Rate**: 85% (measured via Redis INFO stats)

**Eviction Policy**: `volatile-lru` (evict keys with TTL set, least recently used)

---

## ADR-008: Server-Sent Events (SSE) for Streaming

**Status**: Accepted (2024-03)

### Context

AI analysis takes 10-30 seconds:
1. Fetch market data (2s)
2. Call Claude API (5-20s)
3. Fetch news sentiment (3s)
4. Generate insights (2s)

**Problem**: Long wait with no feedback = bad UX.

**Goal**: Stream progress updates to frontend.

### Decision

**Server-Sent Events (SSE)** for real-time updates:

```javascript
// Frontend
const eventSource = new EventSource('/api/analysis/insights');
eventSource.addEventListener('progress', (e) => {
  const { step, progress } = JSON.parse(e.data);
  setProgress(progress); // Update progress bar
});
eventSource.addEventListener('insight', (e) => {
  addInsight(JSON.parse(e.data)); // Append insight
});
```

```java
// Backend
SseEmitter emitter = new SseEmitter();
executor.submit(() -> {
  emitter.send(SseEmitter.event().name("progress").data("{\"step\":\"fetching\",\"progress\":10}"));
  // ... analysis logic
  emitter.send(SseEmitter.event().name("insight").data(insight));
  emitter.complete();
});
return emitter;
```

### Alternatives

**1. WebSocket**:
- **Pros**: Bidirectional, lower latency, full-duplex
- **Cons**: More complex, requires WS library, connection management, not HTTP
- **Rejected**: Overkill for server→client streaming (no client→server needed)

**2. Long Polling**:
- **Pros**: Works with any HTTP client, simple
- **Cons**: Inefficient (repeated requests), higher latency
- **Rejected**: SSE more efficient for streaming

**3. GraphQL Subscriptions**:
- **Pros**: Type-safe, integrated with GraphQL
- **Cons**: Requires GraphQL backend, WebSocket under the hood
- **Rejected**: Backend uses REST, not GraphQL

**4. gRPC Streaming**:
- **Pros**: High performance, bidirectional, type-safe
- **Cons**: Binary protocol (not browser-friendly), requires HTTP/2
- **Rejected**: SSE simpler for web frontend

### Consequences

**Positive**:
- ✅ **Simple**: Built into browsers (EventSource API), Spring MVC (SseEmitter)
- ✅ **HTTP**: Works over HTTP/1.1, no special protocol
- ✅ **Auto-Reconnect**: Browser retries on connection loss
- ✅ **Efficient**: Single connection, multiple events

**Negative**:
- ❌ **One-Way**: Server→Client only (acceptable for progress updates)
- ❌ **Browser Limits**: Max 6 concurrent SSE connections per domain
- ❌ **No Binary**: Text only (JSON encoded)

**Use Cases**:
- Analysis progress (10-30s)
- Backtest results (streaming trade log)
- Real-time quotes (alternative to WebSocket)

---

## ADR-009: ChromaDB for RAG System

**Status**: Accepted (2025-12, v0.7.0)

### Context

**Goal**: Improve AI report quality with historical news context.

**Problem**: Claude API has no knowledge of recent company-specific news:
- Product launches, earnings calls, management changes
- Model knowledge cutoff (January 2025)

**Solution**: RAG (Retrieval-Augmented Generation):
1. Crawl news articles for ticker (e.g., "AAPL AI chip")
2. Embed articles into vector database
3. Semantic search: "AAPL investment analysis" → top 5 relevant articles
4. Inject articles into Claude prompt

**Requirements**:
- Lightweight (embed in AI service, no separate service)
- Fast search (<100ms)
- Python-native
- No heavy dependencies (no PyTorch)

### Decision

**ChromaDB** with ONNX embeddings:
- Embedded database (SQLite + DuckDB)
- Default embedding function (ONNX MiniLM, 50MB)
- Persistent storage (`ai/rag_data/`)

**Architecture**:
```
News URLs → BeautifulSoup Crawler → ChromaDB (vector store)
                                          ↓
User requests report → Semantic search → Inject top 5 articles → Claude API
```

### Alternatives

**1. Pinecone**:
- **Pros**: Fully managed, scalable, fast
- **Cons**: Cost ($70/month), vendor lock-in, network dependency
- **Rejected**: Embedded solution preferred for MVP

**2. Weaviate**:
- **Pros**: Open source, scalable, feature-rich
- **Cons**: Requires separate service (Docker), heavier (Go binary)
- **Rejected**: ChromaDB lighter for embedded use case

**3. FAISS** (Facebook AI Similarity Search):
- **Pros**: Extremely fast, battle-tested, lightweight
- **Cons**: No built-in embeddings, manual index management
- **Rejected**: ChromaDB higher-level API

**4. pgvector** (Postgres extension):
- **Pros**: Integrated with existing database, no extra service
- **Cons**: Postgres not in AI service, slower than specialized vector DB
- **Rejected**: AI service doesn't have Postgres

**5. Elasticsearch**:
- **Pros**: Full-text search + vector search, mature
- **Cons**: Heavy (JVM, 1GB+ RAM), complex setup
- **Rejected**: Overkill for RAG use case

### Consequences

**Positive**:
- ✅ **Lightweight**: 50MB ONNX model (vs 500MB+ PyTorch)
- ✅ **Embedded**: No separate service to deploy
- ✅ **Fast**: <50ms search (1000 documents)
- ✅ **Simple**: 100 lines of code (store.py, crawler.py)

**Negative**:
- ❌ **Scalability**: Embedded = single-node (acceptable for 10K documents)
- ❌ **Embedding Quality**: ONNX MiniLM weaker than OpenAI (acceptable tradeoff)
- ❌ **Storage**: 1MB per document (mitigated: prune old articles)

**Performance**:
- Indexing: 10 articles in 2s
- Search: 1000 documents in 30ms
- Storage: 1000 articles = ~500MB

**Future**: Upgrade to Pinecone if document count exceeds 100K.

---

## ADR-010: Resilience4j Circuit Breaker

**Status**: Accepted (2024-02)

### Context

**Problem**: AI service failures cascade to backend:
- Claude API rate limit → 429 errors
- Network timeout → 30s hang
- Service restart → Connection refused

**Impact**:
- Backend threads blocked waiting for AI service
- All requests slow down (even non-AI endpoints)
- Poor user experience (timeouts, errors)

**Goal**: Fail fast when AI service is unhealthy.

### Decision

**Resilience4j Circuit Breaker** on Backend → AI Service calls:

```
[Closed] → All requests pass through
  ↓ (>50% failure rate in 10 requests)
[Open] → All requests fail immediately (503)
  ↓ (Wait 60s)
[Half-Open] → Test with 3 requests
  ↓ (Success)
[Closed]
```

**Configuration**:
```yaml
resilience4j.circuitbreaker:
  instances:
    aiService:
      failureRateThreshold: 50          # Open after 50% failures
      slidingWindowSize: 10             # Last 10 requests
      waitDurationInOpenState: 60s      # Wait before half-open
      permittedNumberOfCallsInHalfOpenState: 3
```

### Alternatives

**1. Hystrix**:
- **Pros**: Netflix-proven, mature
- **Cons**: In maintenance mode (no new features), heavier
- **Rejected**: Resilience4j is successor, actively maintained

**2. Retry Only** (no circuit breaker):
- **Pros**: Simpler, eventually succeeds
- **Cons**: Wastes resources retrying during outage, slow failure
- **Rejected**: Circuit breaker prevents thundering herd

**3. Manual Health Check**:
- **Pros**: Full control
- **Cons**: Complex to implement correctly, reinventing the wheel
- **Rejected**: Resilience4j battle-tested

**4. Service Mesh** (Istio, Linkerd):
- **Pros**: Infrastructure-level (not code), supports all languages
- **Cons**: K8s required, operational complexity
- **Rejected**: Overkill for 3-service architecture

### Consequences

**Positive**:
- ✅ **Fail Fast**: 503 returned in 10ms (vs 30s timeout)
- ✅ **Cascade Prevention**: Backend remains responsive even if AI service is down
- ✅ **Auto-Recovery**: Circuit closes automatically after 60s
- ✅ **Observability**: Metrics via Actuator (/actuator/health)

**Negative**:
- ❌ **False Positives**: Transient errors can open circuit unnecessarily
- ❌ **User Experience**: Users see 503 error (mitigated: clear error message)

**Metrics**:
- Circuit state: CLOSED, OPEN, HALF_OPEN
- Failure rate: 0-100%
- Slow call rate: 0-100%

---

## ADR-011: Flyway for Database Migrations

**Status**: Accepted (2024-01)

### Context

**Problem**: Database schema evolves with code:
- New features require new tables/columns
- Multiple developers, multiple branches
- Production database must be upgraded safely
- Need rollback capability

**Requirements**:
- Version-controlled migrations
- Automatic execution on deployment
- Idempotent (safe to re-run)
- Support rollback

### Decision

**Flyway** for database migrations:

```
backend/src/main/resources/db/migration/
├── V1__init.sql                   # Initial schema
├── V2__backtest_runs.sql          # Add backtest table
├── V3__create_market_cache.sql    # Add cache table
├── ...
└── V18__add_performance_indexes.sql # Latest
```

**Naming**: `V{version}__{description}.sql`

**Execution**: Automatic on Spring Boot startup.

### Alternatives

**1. Liquibase**:
- **Pros**: Database-agnostic (XML/YAML), advanced rollback, preconditions
- **Cons**: More complex, verbose XML
- **Rejected**: SQL migrations simpler, team prefers SQL

**2. Manual SQL Scripts**:
- **Pros**: Full control, no dependencies
- **Cons**: Error-prone, no version tracking, manual execution
- **Rejected**: Too risky for production

**3. JPA/Hibernate Auto-DDL** (`spring.jpa.hibernate.ddl-auto=update`):
- **Pros**: Automatic, no migration files
- **Cons**: Dangerous in production (data loss risk), no version control
- **Rejected**: Never use in production

**4. golang-migrate**:
- **Pros**: Language-agnostic, CLI tool
- **Cons**: Not integrated with Spring Boot, separate tool to run
- **Rejected**: Flyway better integration with Spring

### Consequences

**Positive**:
- ✅ **Version Control**: Migrations in Git, code review process
- ✅ **Automatic**: Runs on startup, no manual steps
- ✅ **Idempotent**: Safe to re-run (Flyway tracks applied migrations)
- ✅ **Team Workflow**: Merge conflicts caught early (V19 vs V19)

**Negative**:
- ❌ **Rollback**: No built-in rollback (must write separate down migrations)
- ❌ **Hotfixes**: Must skip version numbers if hotfix deployed first

**Best Practices**:
- Never modify existing migrations (V1-V18 are immutable)
- Always add new migration (V19, V20, ...)
- Test migrations on staging first
- Include rollback plan in comments

---

## ADR-012: NumPy Vectorization for Monte Carlo

**Status**: Accepted (2025-12, v0.7.0)

### Context

**Problem**: Monte Carlo simulation is slow (Python loops):
```python
# 10,000 simulations × 252 days = 2,520,000 iterations
paths = []
for sim in range(10000):
    path = [last_price]
    for day in range(252):
        path.append(path[-1] * exp(drift + sigma * random.normal()))
    paths.append(path)
# Runtime: 5-10 seconds
```

**Bottleneck**: Python loops are interpreted (not compiled).

**Goal**: <100ms for 10K simulations.

### Decision

**Vectorize with NumPy** (matrix operations):

```python
# Generate all random shocks at once (10,000 × 252 matrix)
shock = sigma * np.sqrt(dt) * np.random.standard_normal((n_sims, n_days))

# Vectorized computation (single operation on entire matrix)
log_increments = drift + shock
log_paths = np.cumsum(log_increments, axis=1)
paths = last_price * np.exp(log_paths)

# Runtime: 50ms (100x faster)
```

**Why Fast**:
- NumPy implemented in C (compiled)
- SIMD instructions (process 4-8 numbers at once)
- No Python interpreter overhead

### Alternatives

**1. Keep Python Loops**:
- **Pros**: Simple, readable
- **Cons**: 100x slower
- **Rejected**: Unacceptable latency

**2. Cython** (compile Python to C):
- **Pros**: Can achieve C-like performance
- **Cons**: Complex build process, type annotations required
- **Rejected**: NumPy vectorization simpler

**3. Numba** (JIT compile):
- **Pros**: Decorate function with @jit, auto-compile
- **Cons**: Extra dependency, compilation overhead
- **Rejected**: NumPy vectorization sufficient

**4. Rust/C++ Extension**:
- **Pros**: Maximum performance
- **Cons**: Complex FFI, hard to maintain, overkill
- **Rejected**: NumPy performance good enough

**5. GPU (CUDA)**:
- **Pros**: Massive parallelism (1000x)
- **Cons**: Requires GPU, complex setup, cost
- **Rejected**: CPU performance sufficient for 10K simulations

### Consequences

**Positive**:
- ✅ **Performance**: 5s → 50ms (100x speedup)
- ✅ **Scalability**: Can handle 50K simulations in 250ms
- ✅ **Code Quality**: 50 lines → 10 lines (cleaner)
- ✅ **No Dependencies**: NumPy already required

**Negative**:
- ❌ **Readability**: Matrix operations less intuitive than loops
- ❌ **Debugging**: Harder to step through (single matrix operation)
- ❌ **Memory**: Requires (n_sims × n_days) × 8 bytes RAM
  - 10K × 252 × 8 = 20MB (acceptable)
  - 1M × 252 × 8 = 2GB (may hit memory limit)

**Performance Benchmarks**:
```
10K simulations:   50ms
50K simulations:  250ms
100K simulations: 500ms
```

---

## Summary Table

| ADR | Decision | Status | Impact |
|-----|----------|--------|--------|
| 001 | Microservices (3 services) | ✅ Accepted | High (architecture) |
| 002 | JWT Authentication | ✅ Accepted | High (security) |
| 003 | React Query | ✅ Accepted | Medium (frontend) |
| 004 | Spring Boot 4.0 + Java 21 | ✅ Accepted | High (backend) |
| 005 | FastAPI + Python 3.12 | ✅ Accepted | High (AI service) |
| 006 | MySQL 8.4 | ✅ Accepted | High (data) |
| 007 | Redis Caching | ✅ Accepted | Medium (performance) |
| 008 | Server-Sent Events | ✅ Accepted | Medium (UX) |
| 009 | ChromaDB RAG | ✅ Accepted | Medium (AI quality) |
| 010 | Resilience4j Circuit Breaker | ✅ Accepted | High (reliability) |
| 011 | Flyway Migrations | ✅ Accepted | Medium (DevOps) |
| 012 | NumPy Vectorization | ✅ Accepted | High (performance) |

---

## Related Documents

- [ENGINEERING_HANDBOOK.md](./ENGINEERING_HANDBOOK.md) - High-level architecture, stack, and developer workflow
- [API.md](./API.md) - REST API reference
- [PRODUCT_MANUAL.md](./PRODUCT_MANUAL.md) - Feature and user flow reference
- [DOCS_INDEX.md](./DOCS_INDEX.md) - Unified documentation map

---

**Last Updated**: 2026-02-05
**Version**: 1.0.0
**Maintainer**: Stock-AI Architecture Team
