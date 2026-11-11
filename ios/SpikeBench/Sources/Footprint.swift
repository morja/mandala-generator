import Darwin
import Foundation

/// The app's physical memory footprint, the number iOS uses to end apps that take too much.
enum Footprint {
    static func info() -> task_vm_info_data_t? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        let kr = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        return kr == KERN_SUCCESS ? info : nil
    }

    static func current() -> UInt64 { info()?.phys_footprint ?? 0 }

    static func lifetimePeak() -> UInt64 { UInt64(info()?.ledger_phys_footprint_peak ?? 0) }

    /// Samples the footprint every 2 ms on its own thread and keeps the highest value.
    final class Sampler: @unchecked Sendable {
        private let lock = NSLock()
        private var peak: UInt64 = 0
        private var running = false

        func start() {
            running = true
            let thread = Thread { [self] in
                while lock.withLock({ running }) {
                    let now = Footprint.current()
                    lock.withLock { peak = max(peak, now) }
                    usleep(2000)
                }
            }
            thread.start()
        }

        func stop() -> UInt64 {
            lock.withLock {
                running = false
                return peak
            }
        }
    }
}
