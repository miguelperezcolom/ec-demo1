import { expect } from '@playwright/test'
import { envFile } from './env'

export async function api(base: string, method: string, path: string, body?: unknown): Promise<any> {
    const response = await fetch(base + path, {
        method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body),
    })
    const text = await response.text()
    if (!response.ok) throw new Error(`${method} ${base}${path}: ${response.status} ${text.slice(0, 300)}`)
    return text ? JSON.parse(text) : null
}

/** Polls until the value is truthy; returns it. */
export async function until<T>(what: string, probe: () => Promise<T>, timeout = 240_000): Promise<T> {
    let value: T | undefined
    await expect.poll(async () => { try { value = await probe() } catch { value = undefined } return !!value },
        { message: what, timeout, intervals: [3_000] }).toBe(true)
    return value as T
}

/** Salesforce, as the MDM's integration user: client credentials from salesforce.env. */
export class Salesforce {
    private token?: { instance: string, access: string }
    private async session() {
        if (!this.token) {
            const env = envFile('salesforce.env')
            const body = new URLSearchParams({ grant_type: 'client_credentials', client_id: env.SF_CLIENT_ID, client_secret: env.SF_CLIENT_SECRET })
            const r = await (await fetch(`https://${env.SF_DOMAIN}/services/oauth2/token`, { method: 'POST', body })).json()
            this.token = { instance: r.instance_url, access: r.access_token }
        }
        return this.token
    }
    async call(method: string, path: string, body?: unknown): Promise<any> {
        const s = await this.session()
        const r = await fetch(`${s.instance}/services/data/v67.0${path}`, {
            method, headers: { Authorization: `Bearer ${s.access}`, 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
        })
        if (!r.ok) throw new Error(`Salesforce ${method} ${path}: ${r.status} ${await r.text()}`)
        const text = await r.text()
        return text ? JSON.parse(text) : null
    }
}

/** Opera (OHIP UAT), read only here: to see what the integration wrote. */
export class Opera {
    private access?: string
    private env = envFile('opera.env')
    private async token() {
        if (!this.access) {
            const auth = Buffer.from(`${this.env.OPERA_CLIENT_ID}:${this.env.OPERA_CLIENT_SECRET}`).toString('base64')
            const r = await (await fetch(`${this.env.OPERA_GATEWAY_URL}/oauth/v1/tokens`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'x-app-key': this.env.OPERA_APP_KEY,
                           enterpriseId: this.env.OPERA_ENTERPRISE_ID, Authorization: `Basic ${auth}` },
                body: new URLSearchParams({ grant_type: 'client_credentials', scope: 'urn:opc:hgbu:ws:__myscopes__' }),
            })).json()
            this.access = r.access_token
        }
        return this.access!
    }
    async get(hotel: string, path: string): Promise<any> {
        const r = await fetch(`${this.env.OPERA_GATEWAY_URL}${path}`, {
            headers: { 'x-app-key': this.env.OPERA_APP_KEY, 'x-hotelid': hotel, Authorization: `Bearer ${await this.token()}`,
                       'x-request-id': crypto.randomUUID(), Accept: 'application/json' },
        })
        if (!r.ok) throw new Error(`Opera GET ${path}: ${r.status} ${await r.text()}`)
        return r.json()
    }
}
