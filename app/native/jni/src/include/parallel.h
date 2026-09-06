#pragma once

//
// Work-queue fan-out shared by the OCR stage loops: one atomic cursor, N
// worker threads, join before return. Callers pass the resolved worker count
// (their own cap: session count, REC_PARALLELISM, ...) and the item count;
// each fn(wi, i) invocation owns item i on worker wi.
//

#include <atomic>
#include <cstddef>
#include <thread>
#include <vector>

namespace ocr {

template <typename Fn>
void parallelFor(size_t workers, size_t items, Fn&& fn) {
    std::atomic<size_t> next{0};
    std::vector<std::thread> pool;
    pool.reserve(workers);
    for (size_t wi = 0; wi < workers; wi++) {
        pool.emplace_back([&, wi] {
            while (true) {
                const size_t i = next.fetch_add(1);
                if (i >= items) break;
                fn(wi, i);
            }
        });
    }
    for (auto& t : pool) t.join();
}

} // namespace ocr
