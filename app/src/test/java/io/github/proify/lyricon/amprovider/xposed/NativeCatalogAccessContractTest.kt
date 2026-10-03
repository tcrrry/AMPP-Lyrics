package io.github.proify.lyricon.amprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeCatalogAccessContractTest {
    class Api {
        @JvmField var t: String? = "us"
        @JvmField var s: Int = 0
        companion object { @JvmField var staticStorefront: String = "us" }
    }
    class Holder {
        companion object {
            val api = Api()
            fun getMediaApi(): Api = api
            fun getMediaApiWithHTTPCache(): Api = Api()
            fun invalid(): Int = 7
        }
    }
    class NullHolder { companion object { fun getMediaApi(): Api? = null } }
    class DuplicateHolder {
        companion object {
            @JvmField val other = this
            fun getMediaApi(): Api = Api()
        }
    }
    open class ParentApi { @JvmField var storefront: String = "us" }
    class InheritedApi : ParentApi()

    @Test fun `uncached API identity survives a cached getter on the same companion`() {
        assertSame(Holder.api, NativeCatalogAccessContract.mediaApi(Holder::class.java, "getMediaApi"))
        assertThrows(IllegalStateException::class.java) {
            NativeCatalogAccessContract.mediaApi(Holder::class.java, "invalid")
        }
        assertThrows(IllegalStateException::class.java) {
            NativeCatalogAccessContract.mediaApi(Holder::class.java, "missing")
        }
    }

    @Test fun `missing null and ambiguous holders cannot supply a native API`() {
        assertThrows(IllegalStateException::class.java) {
            NativeCatalogAccessContract.mediaApi(Api::class.java, "getMediaApi")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCatalogAccessContract.mediaApi(NullHolder::class.java, "getMediaApi")
        }
        assertThrows(IllegalStateException::class.java) {
            NativeCatalogAccessContract.mediaApi(DuplicateHolder::class.java, "getMediaApi")
        }
    }

    @Test fun `1606 storefront writes and restores the profiled instance slot only`() {
        val api = Api()
        val field = NativeCatalogAccessContract.storefrontField(api, "t")
        val account = field.get(api)
        try {
            field.set(api, "jp")
            assertEquals("jp", api.t)
            assertEquals(0, api.s)
            assertEquals("us", Api.staticStorefront)
        } finally {
            field.set(api, account)
        }
        assertEquals("us", api.t)
        listOf("s", "staticStorefront", "missing").forEach { name ->
            assertThrows(IllegalStateException::class.java) {
                NativeCatalogAccessContract.storefrontField(api, name)
            }
        }
    }

    @Test fun `inherited storefront stays supported`() {
        val api = InheritedApi()
        NativeCatalogAccessContract.storefrontField(api, "storefront").set(api, "cn")
        assertEquals("cn", api.storefront)
    }
}
