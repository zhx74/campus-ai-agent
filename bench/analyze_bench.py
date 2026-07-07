"""
Benchmark Result Analyzer
Usage: python analyze_bench.py
Reads CSV files from bench/ directory and generates statistics report.
"""
import csv
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

BENCH_DIR = os.path.dirname(os.path.abspath(__file__))

def load_csv(filename):
    path = os.path.join(BENCH_DIR, filename)
    if not os.path.exists(path):
        return None
    latencies = []
    errors = 0
    timestamps = []
    with open(path, 'r') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 7:
                continue
            timestamps.append(int(row[0]))
            latencies.append(float(row[1]))
            if row[5].strip().lower() != 'true':
                errors += 1
    return latencies, errors, timestamps

def calc_stats(latencies):
    latencies_sorted = sorted(latencies)
    n = len(latencies_sorted)
    if n == 0:
        return {}
    return {
        'count': n,
        'avg': sum(latencies) / n,
        'min': latencies_sorted[0],
        'max': latencies_sorted[-1],
        'p50': latencies_sorted[int(n * 0.50)],
        'p90': latencies_sorted[int(n * 0.90)],
        'p95': latencies_sorted[int(n * 0.95)],
        'p99': latencies_sorted[int(n * 0.99)],
    }

def calc_throughput(timestamps):
    if len(timestamps) < 2:
        return 0
    duration_s = (max(timestamps) - min(timestamps)) / 1000.0
    if duration_s == 0:
        return 0
    return len(timestamps) / duration_s

def print_stats(name, stats, errors, throughput=None):
    print(f"\n{'='*60}")
    print(f"  {name}")
    print(f"{'='*60}")
    print(f"  Total Requests : {stats['count']}")
    print(f"  Errors         : {errors} ({errors/stats['count']*100:.2f}%)")
    print(f"  Avg            : {stats['avg']:.1f} ms")
    print(f"  Min            : {stats['min']:.0f} ms")
    print(f"  Max            : {stats['max']:.0f} ms")
    print(f"  P50 (Median)   : {stats['p50']:.0f} ms")
    print(f"  P90            : {stats['p90']:.0f} ms")
    print(f"  P95            : {stats['p95']:.0f} ms")
    print(f"  P99            : {stats['p99']:.0f} ms")
    if throughput:
        print(f"  Throughput     : {throughput:.0f} req/s")

def main():
    print("\n" + "=" * 60)
    print("  CAMPUS CANTEEN BENCHMARK REPORT")
    print("=" * 60)

    # === Test 1: Cache A/B ===
    print("\n\n>>> TEST 1: Cache A/B Comparison (Same Endpoint)")
    print("    Endpoint: GET /user/dish/list?categoryId=1")

    cold = load_csv('phase_a_cold_cache.csv')
    warm = load_csv('phase_c_warm_cache.csv')

    if cold and warm:
        cold_stats = calc_stats(cold[0])
        warm_stats = calc_stats(warm[0])
        cold_tp = calc_throughput(cold[2])
        warm_tp = calc_throughput(warm[2])

        print_stats("Phase A - Cold Cache (DB Hit)", cold_stats, cold[1], cold_tp)
        print_stats("Phase C - Warm Cache (Redis Hit)", warm_stats, warm[1], warm_tp)

        if cold_stats['p50'] > 0:
            improvement = (cold_stats['p50'] - warm_stats['p50']) / cold_stats['p50'] * 100
            print(f"\n  --- Comparison ---")
            print(f"  P50 Improvement : {cold_stats['p50']:.0f}ms -> {warm_stats['p50']:.0f}ms ({improvement:+.1f}%)")
            avg_improvement = (cold_stats['avg'] - warm_stats['avg']) / cold_stats['avg'] * 100
            print(f"  Avg Improvement : {cold_stats['avg']:.1f}ms -> {warm_stats['avg']:.1f}ms ({avg_improvement:+.1f}%)")
            tp_improvement = (warm_tp - cold_tp) / cold_tp * 100 if cold_tp > 0 else 0
            print(f"  Throughput Gain : {cold_tp:.0f} -> {warm_tp:.0f} req/s ({tp_improvement:+.1f}%)")
    else:
        print("  [!] Missing CSV files. Run cache-ab-test.jmx first.")

    # === Test 2: Stepped Load ===
    print("\n\n>>> TEST 2: Stepped Load Test (10 -> 100 threads)")

    stepped = load_csv('stepped_load.csv')
    if stepped:
        stepped_stats = calc_stats(stepped[0])
        stepped_tp = calc_throughput(stepped[2])
        print_stats("Stepped Load (10-100 threads)", stepped_stats, stepped[1], stepped_tp)

        # Analyze by time windows (each step is ~30s)
        timestamps = stepped[2]
        latencies = stepped[0]
        t0 = min(timestamps)
        steps = [
            (0, 30, "10 threads"),
            (30, 60, "30 threads"),
            (60, 90, "50 threads"),
            (90, 120, "80 threads"),
            (120, 150, "100 threads"),
        ]
        print(f"\n  --- Per-Step Breakdown ---")
        print(f"  {'Step':<16} {'Requests':>8} {'Avg':>8} {'P50':>8} {'P95':>8} {'Errors':>8}")
        for start, end, label in steps:
            step_lat = []
            step_err = 0
            for i, ts in enumerate(timestamps):
                elapsed = (ts - t0) / 1000.0
                if start <= elapsed < end:
                    step_lat.append(latencies[i])
            if step_lat:
                s = calc_stats(step_lat)
                print(f"  {label:<16} {s['count']:>8} {s['avg']:>7.1f}ms {s['p50']:>7.0f}ms {s['p95']:>7.0f}ms {step_err:>8}")
    else:
        print("  [!] Missing CSV file. Run stepped-load.jmx first.")

    # === Test 3: Sustained Load ===
    print("\n\n>>> TEST 3: Sustained Load (50 threads, 120s)")

    sustained = load_csv('sustained_load.csv')
    if sustained:
        sustained_stats = calc_stats(sustained[0])
        sustained_tp = calc_throughput(sustained[2])
        print_stats("Sustained Load (50 threads, 120s)", sustained_stats, sustained[1], sustained_tp)
    else:
        print("  [!] Missing CSV file. Run sustained-load.jmx first.")

    print(f"\n\n{'='*60}")
    print("  Analysis complete.")
    print(f"{'='*60}\n")

if __name__ == '__main__':
    main()
