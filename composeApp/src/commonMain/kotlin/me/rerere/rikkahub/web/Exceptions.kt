package me.rerere.rikkahub.web

sealed class ApiException(
    override val message: String,
    val statusCode: Int,
) : RuntimeException(message)

class BadRequestException(message: String) : ApiException(message, 400)
class UnauthorizedException(message: String) : ApiException(message, 401)
class ForbiddenException(message: String) : ApiException(message, 403)
class NotFoundException(message: String) : ApiException(message, 404)
class ConflictException(message: String) : ApiException(message, 409)
