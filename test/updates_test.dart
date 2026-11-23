import 'package:astra_dpf_monitor/updates.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const release = <String, Object>{
    'tag_name': 'v0.2.0+12',
    'draft': false,
    'prerelease': false,
    'assets': [
      {'name': 'dpf-astra.apk', 'state': 'uploaded'},
    ],
  };

  test(
    'only newer Android builds are offered, even at the same version name',
    () {
      expect(ReleaseUpdate.fromGitHub(release, 11)?.build, 12);
      expect(ReleaseUpdate.fromGitHub(release, 12), isNull);
      expect(ReleaseUpdate.fromGitHub(release, 13), isNull);
    },
  );

  test('incomplete and unrelated releases are ignored', () {
    for (final response in [
      null,
      {...release, 'draft': true},
      {...release, 'prerelease': true},
      {...release, 'tag_name': 'v0.2.0'},
      {...release, 'assets': []},
      {
        ...release,
        'assets': [
          {'name': 'dpf-astra.apk', 'state': 'new'},
        ],
      },
    ]) {
      expect(ReleaseUpdate.fromGitHub(response, 1), isNull);
    }
  });
}
