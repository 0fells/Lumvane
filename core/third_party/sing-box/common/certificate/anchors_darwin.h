#ifndef BOX_CERTIFICATE_ANCHORS_DARWIN_H
#define BOX_CERTIFICATE_ANCHORS_DARWIN_H

#include <stddef.h>
#include <stdint.h>

void *box_certificate_anchors_from_der(const uint8_t *const *ders, const size_t *lens, size_t count);

void box_certificate_release_anchors(void *anchors);

#endif
