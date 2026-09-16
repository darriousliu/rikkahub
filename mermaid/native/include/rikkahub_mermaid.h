#ifndef RIKKAHUB_MERMAID_H
#define RIKKAHUB_MERMAID_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MermaidResult MermaidResult;

/* UTF-8 input is borrowed for this call. A zero length accepts a null source.
 * The result owns its strings and must be freed once, even after an error.
 * Both getters return null when the source contains no diagram.
 * Each call is independent; never access a result concurrently with freeing it. */
MermaidResult *rikkahub_mermaid_render_svg(const uint8_t *source, uint64_t length);
/* Optional site-level Mermaid configuration as UTF-8 JSON; an empty buffer uses defaults. */
MermaidResult *rikkahub_mermaid_render_svg_with_config(
    const uint8_t *source, uint64_t length, const uint8_t *config, uint64_t config_length);
const char *rikkahub_mermaid_result_svg(const MermaidResult *result);
const char *rikkahub_mermaid_result_error(const MermaidResult *result);
void rikkahub_mermaid_result_free(MermaidResult *result);

#ifdef __cplusplus
}
#endif

#endif
