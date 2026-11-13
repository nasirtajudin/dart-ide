// Dart IDE sample project — lib/main.dart
// Everything here runs fully offline on your device.
// (Run lands in Phase 3 — for now, enjoy the highlighting.)

class Greeter {
  final String name;

  Greeter(this.name);

  String greet() => 'Hello, $name!';
}

int fibonacci(int n) {
  if (n <= 1) return n;
  return fibonacci(n - 1) + fibonacci(n - 2);
}

num average(List<num> values) {
  var sum = 0;
  for (final v in values) {
    sum += v;
  }
  return sum / values.length;
}

void main() {
  final greeter = Greeter('Dart IDE');
  print(greeter.greet());

  for (var i = 0; i < 10; i++) {
    print('fib($i) = ${fibonacci(i)}');
  }

  var scores = [9.5, 8.0, 10.0, 7.25];
  print('average = ${average(scores)}');
}
