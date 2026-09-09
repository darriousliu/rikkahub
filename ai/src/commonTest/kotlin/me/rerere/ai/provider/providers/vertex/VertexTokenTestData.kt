package me.rerere.ai.provider.providers.vertex

// Synthetic 2048-bit test key generated with OpenSSL; never associated with a real service account.
// Expected signatures were produced independently using openssl dgst -sha256 -sign.
internal object VertexTokenTestData {
    const val EMAIL = "test-service@example.test"
    const val NOW = 1_800_000_000L
    const val MESSAGE = "RikkaHub Vertex RS256 contract — 测试\n"
    val privateKeyPem = """
        -----BEGIN PRIVATE KEY-----
        MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCF1i/STXreIvnD
        kUNI/TkpTjDG/RJGsHhvih3xaNZ9CWa6184oKoJslpZOCwjGCdrnQ/vzx7qBVsVU
        IvwA/9mwJi5SGKPf14w0++3CU+Avb7wIPSlNIKSVFIizDiNrZ5z5adGWQdic8Xan
        +VzaWqm4UGqSUZK7QuQMieW6bWxQe5bxbu75MFw68FvrxsJRLLNPt6IjXTybY4UA
        MP6oht+Y4QWcAHkq8Fk5dERfQFZpIMp2AJBwS9prjp1d0WI142XsxIXDHQOdzFD7
        zi48Ntf9/jKpVIFyFoGqCpoQP1Hf0HKkADgzRTJ1Qlbx9sdi6VrEXf5+5tqvAs9V
        6oJahuQnAgMBAAECggEACPIs4Xp7BgeBznK9SU8JLRukOF4zhIod4sbGrZumYAFA
        M/5iMgkgDVtw0ef8PtCV00jrZHuAmfik2V+P8g59YergxG1lNtHU9g2znNO3yTPy
        IP2FmeQ0BssH6tVnxLF5AABKGX3D7umtAW6yUt4l424XUY8mUbds6vGkwMtjpsAj
        XZ1cJyUALbxVfjp0rrMmPGyQlT3PhxK0/FqoOy3q1GH76XSr3/2eFbjP7u/Tyktg
        iXFCpeHnBO8Ssbgx3KF8Mu+0bJPmnv89LRgI2EKH35Uh8KzSZg4PhGoXmw3ZU1xe
        lNgXNj5lRZCTLXevz9BB3jyY19G/wcg4aKpViRU0gQKBgQC68dKYJHi3Hpd0cMU9
        it/AgDHVP8klt2rX0cNIkpmGlB/kdYOcWaaiZ5QXI0zYRVTXFDXVnYDZ1WdcuMME
        fjkPCZ5KzQFonU0zc/ToIXnKjmqFYleR/OaqRzfaKhHa0bek53VH80vW5I2IdCHp
        md9LhOJr0gVEqvb+LsUI7344hwKBgQC3RkdsCLz7on4Jzz0IqVMqjAmoKXKyDUEZ
        Ey2W45VWs9Pt87CjqjPkhLko7KT8mAVx1Y0XXMD54CuPS7XCOPg/fLtJPinBZu2X
        17jTFICofH3PTL+XDCBcU1chnpBSKwW3k2bR9YvVRpN9/+d9767CFIXLh/3HNaRy
        jCmrjbP/YQKBgGWTKWMDRhfxZkedUn5a67JztR/yVX/JxiLX1aykAwkNiLqgY09b
        ot/RObTMuF82ZJ+sWofj2XQCsPO/bC8Pyj7ycfCgrShDeAQB+Er8jWlSsmwhAAXR
        jR9uLNm8TBFCK+9M1/utJULw0joXJ6n+skYdQM1mwvgJkMSRcvuk7UcXAoGAMdP/
        sxWlG90P22bmtyYiIF02euvZ7SLBYineqTly7sGxiu5IRhcOSMD0/nVoXeyO/mYL
        GCD1rnoOHcV9wzJt/ATTfpRSEe0EB9Z0v57BZA2EvwmkxVJcsf1OoStVHaHoygJf
        YBIcVH39t7INpCW4I7gksDNnAfRJo4MMCUJbf8ECgYAjyYFh7EywnXp+ZJEUP8AO
        5Wg6pdYtkfcmEv1ygMXBbCaFvjlYvGcuC1cU5se62OrBYI+AUZGjjQV9OQViytWb
        EIBQwy2tXLkYJvX8zGY4bAcHE2YXwyntksCRfAGpFkCWGMhqbiybo7n1cZ6NPuif
        6B8PnkxUcfaUnUUnJFkxKQ==
        -----END PRIVATE KEY-----
    """.trimIndent()

    const val messageSignature =
        "fZmVBipxV7ZvOVsiQOZBHflLqylVaoFTEvT-2_9PFKdvdsnX8_Q_eMqFEPQdrv3uYrV4z2kly7puoZBg1E64Z51d" +
        "g3tpfF3SSjkpZKGW01C1PHkTiITljDklzpgrUM7hZocZNZv1QZJ_va3LiLC8qwEBsAGyq4uNAB9sjeEMLdcBl_ml" +
        "hSX7iYw0R9p8JzrhVQQLkERCdfssMLhgUMI8l9AtWkhAwrY_QRF1D5x_2RMJSBH7mXDEL-VOF0sTfEFZGam7bJ_r" +
        "5_lJiuHWOwLiOnDF4-aGBxtzVliW9uF7v-uoK7ETm1gkhVQfSqO_J_4ybupgLeFLNcRCMnfXv2AxSQ"

    const val defaultJwtSignature =
        "XZ8KZwNeI23cxmqEMNeqWHvmE9NsNE5Je3lIpQlrVlajhR655_3VhgBEF0AiIfT72sZzs-5ziCZ9odpgX9S_8d4q" +
        "MnRv63rY92OM8hdC1WBIfc5Eg4LflgyXrBGHhXmAEjPqT0oX76tSQpG9gl43f0KFyT_g7vwot4LndO01BE3qsWY2" +
        "cJLxVWdCsue-BBbWBh5w-_QjNOUHStCTPKhKBMGwliPjBaezmgEVgJpN4nvVur4dSInWUPZtUwzAO1gY_qsjQa-P" +
        "akjKA2-gttKEwtLWPc8mVGX17imKV2J7P-7172ZQpSy-3mIWfJa4Z3m-5GPSNHGFk-3kWlH8HzcWQg"

}
