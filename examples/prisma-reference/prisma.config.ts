import { defineConfig } from 'prisma/config'

export default defineConfig({
  schema: 'prisma/schema.prisma',
  // Generation needs a syntactically valid URL, not a live database. Runtime
  // tests require an explicit local transaction_lab URL and validate it.
  datasource: {
    url:
      process.env.TX_DATABASE_URL ??
      'mysql://lab:lab-only-password@127.0.0.1:3306/transaction_lab',
  },
})
