package me.rerere.document

// Synthetic archives; expected output captured from tag 2.4.5 on Android (including its XML parser).
internal data class DocumentFixture(val base64: String, val expected: String)

internal val documentFixtures = mapOf(
    "CMP79.docx" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL12aWLQsTgAAAAAAAQAKAAAAdW51c2VkLmJpbu3BAQEAAACAkNvN7wgKAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAalBLAwQUAAAACABwbS9dwJuNa0QBAAAfAwAAEQAAAHdv" +
            "cmQvZG9jdW1lbnQueG1svVNNTgIxFN5zitoFS4ssNA5DExhidGEkwYUJYVE6FRr7l7YwcgJv4C3ceyDjOZxpZyCK4M7N6+t73/e+" +
            "9GubFkmu6Uoy5cGzFMolRR8uvTcJQo4umSTuVBumyt6jtpL4cmsXqNA2N1ZT5hxXCylQt9M5R5JwBXFaJHOdb3CrTEy1M2Mblonf" +
            "CAaKZE1EH14zkpfULkQ4RVtMCB5nt+OLSzC6yx7Ax/vb5+tLBfEBaCN8Nz3yIn2OqsjjyLrm8VCLHLSJND2Qnkyz0eB+MOWeCE5n" +
            "Mwzawvf0U3vhe3+JBIcSZwhlfWgsc8yuGcQAgCPM+mBqJWPCxVo0HpxBVPdu8qbWGFIT9qy54tb5f9SbMKpV/rugn4sAilCKv7uF" +
            "qytmFgz2yaFCj7KGh1loK3lAOGNCHHw4P6ageIxWlYRnWyXNl8BfUEsBAhQDFAAAAAgAcG0vXZpYtCxOAAAAAAABAAoAAAAAAAAA" +
            "AAAAAIABAAAAAHVudXNlZC5iaW5QSwECFAMUAAAACABwbS9dwJuNa0QBAAAfAwAAEQAAAAAAAAAAAAAAgAF2AAAAd29yZC9kb2N1" +
            "bWVudC54bWxQSwUGAAAAAAIAAgB3AAAA6QEAAAAA",
        "## CMP79 DOCX 中文\n\n***Bold & italic <ok>***\n\n  1. First\n  1. Second\n| Header A | Header B | \n| --- | --- | \n| Cell 中文 |  |"
    ),
    "CMP79.epub" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL10MUiDkZwAAAI0AAAAWAAAATUVUQS1JTkYvY29udGFpbmVyLnhtbE3MMQ7DIAyF4atE7Cm7RbhCIuUEiBgV" +
            "CWyEjZTjJ+2QdnvD/z0XmTRkwj6dtZAsZnQCDpIFKFQU0AjckA6OoyIpfDN4mPGuM2vKBeU3pzRKmVvQ92LWbbef/MYvbslY7+wf" +
            "sc+VvwBQSwMEFAAAAAgAcG0vXQ1Pgu2yAAAAfQEAAA8AAABPUFMvY29udGVudC5vcGadkN0OgjAMRl9l2a1xNd6YGOBdFuigkW0N" +
            "GwHf3vGziHrnXfN1PWdtwbp+6BbFbHsXStnFyHeAaZoUNWyUH1q4Xi438GxkVVjtyGCIVUERraCmlN6hFN2AZi3V3EXbS2GxIX2O" +
            "T8ZSauaeah3JO1jbpySTcGDEyWdGKv9jkE1rZMroxoCNYtd+YtZHsMRpFN7bBCaHGyzNJ17+S1Yc0k30m1sKgTbyV2c50eLbLbDf" +
            "vHoBUEsDBBQAAAAIAHBtL10FlnU++AAAAEcBAAANAAAAT1BTL29uZS54aHRtbE2QQU7DMBBFrzLJgl06oixQYDpSSYNgAQ0iLBDq" +
            "Io2t1MKOQ2KUcgJuwC3YcyDEOeokXbD5mjd+kmeGgtU6yZ+zFHbOaKYhYW903S3CnXPNBWLf97P+bGbbCk/jOMb94IRelYVgcspp" +
            "yTdKCFnDCIRTj3AytlZ8eHvOyV12HkOaPV3B+j71z3OmhnO5d3BSmOYSKHhJVst8OeVmwxREEXSNVg6iiOH35/vv6xOoc62tK95a" +
            "LQiPQNKwcoVWJaEvCRsm6xfSiq9V2zlCXw30KEtbiwlxMJSpoNBuEd6aopLHT0L0g2tbvr69Wyf5YUjCfx0P41443u0AUEsDBBQA" +
            "AAAIAHBtL12ywRt1+AAAAEcBAAANAAAAT1BTL3R3by54aHRtbE2QQU7DMBBFrzLJgl06oixQYDpSSYtggRpEKoRQF2lspRZ2HBKj" +
            "hBNwA27BngMhzoGTdMHma974SZ4ZClabJHtK13BwRjMNCb3RVbsID87VF4hd1826s5ltSjyN4xj7wQm9KnPB5JTTkm+UELKCEQin" +
            "HuFk7K149/ack7v0PIZ1ur2C7HHjn+dMNWeyd3CSm/oSKHhOVstsOeVuxxREEbS1Vg6iiOHn++v38wOodY2tSt5bLQiPQNKwcrlW" +
            "BaEvCWsm6xfSiq9V0zpCXw30IAtbiQlxMJQpIdduEd6avJTHT0L0g2tbvLy+WSf5fkjCfx0P41443u0PUEsBAhQDFAAAAAgAcG0v" +
            "XQxSIORnAAAAjQAAABYAAAAAAAAAAAAAAIABAAAAAE1FVEEtSU5GL2NvbnRhaW5lci54bWxQSwECFAMUAAAACABwbS9dDU+C7bIA" +
            "AAB9AQAADwAAAAAAAAAAAAAAgAGbAAAAT1BTL2NvbnRlbnQub3BmUEsBAhQDFAAAAAgAcG0vXQWWdT74AAAARwEAAA0AAAAAAAAA" +
            "AAAAAIABegEAAE9QUy9vbmUueGh0bWxQSwECFAMUAAAACABwbS9dssEbdfgAAABHAQAADQAAAAAAAAAAAAAAgAGdAgAAT1BTL3R3" +
            "by54aHRtbFBLBQYAAAAABAAEAPcAAADAAwAAAAA=",
        "## CMP79 EPUB TWO\n\nText & CDATA 中文 **bold***italic*\n\n1. First\n2. Second\n\n[image: Image 中文]> Quote\n\n## CMP79 EPUB ONE\n\nText & CDATA 中文 **bold***italic*\n\n1. First\n2. Second\n\n[image: Image 中文]> Quote"
    ),
    "CMP79.pptx" to DocumentFixture(
        "UEsDBBQAAAAIADJuL111qV1/JAEAAGQCAAAVAAAAcHB0L3NsaWRlcy9zbGlkZTIueG1slZKxTsMwEIZfxWRg5AoDiJBYSoLYEJUa" +
            "iaHq4CQmsWQ7lu1C2dh4Ax6AnZ3nQYjnIHbaREWRgOU/n3333Z3OkQoNr9BGcGlCFQeNtSoEMGVDBTFHraKye7trtSC2c3UNSlND" +
            "pSWWtVJwOJnNTkEQJoMthPwFUmnywGS9l48jFZYLXjlrVK4p7U9O7SZtq0cckVA50V7mGhVxcBwg5hTcncXZ9fzsHC04qyjKb2/Q" +
            "IRHqAkUHy+wyyZOlJYyvVjgCF+tUe1UDWnVUfs8d0bnFOmuIRmUncfDx9OrKgI8a+rA4XXNOLfp8f/t6eZ5AwzgA7CaqNVENK680" +
            "EdRTCu5Njyy9TgxtcTJVYAyGPhsG1K/A9D9A2HYKPyeAcWmw2yP4z4W/AVBLAwQUAAAACAAybi9d6oAuTCMBAABkAgAAFQAAAHBw" +
            "dC9zbGlkZXMvc2xpZGUxLnhtbJWSsU7DMBCGX8VkYOQKA4iQWGoCbECldqs6OIlJLNnOyXGhbGy8AQ/Azs7zIMRzEDttIlAkYPnP" +
            "Z999d6dzhGEjC7JRUjchxkFlLYYATV5xxZqDGrlu325ro5htXVMCGt5wbZkVtVYSjiaTY1BM6GALYX+BFIbdC11+y6cRhvlcFs42" +
            "uDCcdyendpPUxQONWIhOjJeZIVkcHAZEOAV3Z2l6NTs5JXMpCk5uri/IPlN4RqK9ZXo+XUyXlgm5WtEIXKxT4xV7NLZUeScd0bnZ" +
            "Oq2YIXkrcfD++OLKgI/q+7A0WUvJLfl4e/18fhpBwzAA7CYqDcNK5JeGKe4pmfSmQ+ZeR4a2dDpWYAiGLht61K/A5D9A2HYKPyeA" +
            "YWmw2yP4z0W/AFBLAwQUAAAACAAybi9dMyShbKsAAAAuAQAAHwAAAHBwdC9ub3Rlc1NsaWRlcy9ub3Rlc1NsaWRlMS54bWyNkDsO" +
            "wjAMhq8S9QAYMTBEaQYGZiROENrQRMrDii1ob0/ThwQby2dbv/3LtkKZMlsSYwyJJLaNY0YJQJ2z0dAho02z9swlGp7LMgAWSzax" +
            "YZ9TDHA6Hs8QjU/NZmL+MemLefs0/MxrhZKwMr3ueCtrtkZ0gie0bfPI/dSAVrBr8N3O42XWtTISK0oF66svxALdRL4zQVDwvRXL" +
            "3QqqXlkW4uK3u8C6D2xP0h9QSwECFAMUAAAACAAybi9ddaldfyQBAABkAgAAFQAAAAAAAAAAAAAAgAEAAAAAcHB0L3NsaWRlcy9z" +
            "bGlkZTIueG1sUEsBAhQDFAAAAAgAMm4vXeqALkwjAQAAZAIAABUAAAAAAAAAAAAAAIABVwEAAHBwdC9zbGlkZXMvc2xpZGUxLnht" +
            "bFBLAQIUAxQAAAAIADJuL10zJKFsqwAAAC4BAAAfAAAAAAAAAAAAAACAAa0CAABwcHQvbm90ZXNTbGlkZXMvbm90ZXNTbGlkZTEu" +
            "eG1sUEsFBgAAAAADAAMA0wAAAJUDAAAAAA==",
        "## Slide 1\n\nCMP79 Slide TWO & tail\n- Bullet 中文\n\n| A | \n| --- | \n| B | \n\n\n### Speaker Notes\n\nFirst physical slide notes\n## Slide 2\n\nCMP79 Slide ONE & tail\n- Bullet 中文\n\n| A | \n| --- | \n| B |"
    ),
    "empty.docx" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL13PAJB/BgAAAAQAAAAJAAAAb3RoZXIudHh0y8vPSwUAUEsBAhQDFAAAAAgAcG0vXc8AkH8GAAAABAAAAAkA" +
            "AAAAAAAAAAAAAIABAAAAAG90aGVyLnR4dFBLBQYAAAAAAQABADcAAAAtAAAAAAA=",
        "Unable to find document content in DOCX file"
    ),
    "empty.epub" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL13PAJB/BgAAAAQAAAAJAAAAb3RoZXIudHh0y8vPSwUAUEsBAhQDFAAAAAgAcG0vXc8AkH8GAAAABAAAAAkA" +
            "AAAAAAAAAAAAAIABAAAAAG90aGVyLnR4dFBLBQYAAAAAAQABADcAAAAtAAAAAAA=",
        "Unable to find OPF file in EPUB"
    ),
    "empty.pptx" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL13PAJB/BgAAAAQAAAAJAAAAb3RoZXIudHh0y8vPSwUAUEsBAhQDFAAAAAgAcG0vXc8AkH8GAAAABAAAAAkA" +
            "AAAAAAAAAAAAAIABAAAAAG90aGVyLnR4dFBLBQYAAAAAAQABADcAAAAtAAAAAAA=",
        "No slides found in PPTX file"
    ),
    "malformed.docx" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL12S++XpXAAAAGkAAAARAAAAd29yZC9kb2N1bWVudC54bWwtyUEKgCAQQNGrRAdopEULse5iahY4M+IY1u0r" +
            "aPV5fNO0Z3dioNpdmEh0m/u91qwBxO0BrQycA71v44K2viwRGhefC7sgclDEBKNSE6A9qF9M0yv7+2teDPx6AFBLAQIUAxQAAAAI" +
            "AHBtL12S++XpXAAAAGkAAAARAAAAAAAAAAAAAACAAQAAAAB3b3JkL2RvY3VtZW50LnhtbFBLBQYAAAAAAQABAD8AAACLAAAAAAA=",
        "Error parsing document XML:"
    ),
    "missing-opf.epub" to DocumentFixture(
        "UEsDBBQAAAAIAHBtL10MUiDkZwAAAI0AAAAWAAAATUVUQS1JTkYvY29udGFpbmVyLnhtbE3MMQ7DIAyF4atE7Cm7RbhCIuUEiBgV" +
            "CWyEjZTjJ+2QdnvD/z0XmTRkwj6dtZAsZnQCDpIFKFQU0AjckA6OoyIpfDN4mPGuM2vKBeU3pzRKmVvQ92LWbbef/MYvbslY7+wf" +
            "sc+VvwBQSwECFAMUAAAACABwbS9dDFIg5GcAAACNAAAAFgAAAAAAAAAAAAAAgAEAAAAATUVUQS1JTkYvY29udGFpbmVyLnhtbFBL" +
            "BQYAAAAAAQABAEQAAACbAAAAAAA=",
        "Unable to read OPF file in EPUB"
    )
)
