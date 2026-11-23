import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';

const _platform = MethodChannel('astra/updates');
final _latestRelease = Uri.https(
  'api.github.com',
  '/repos/lohacker10/dpf_astra/releases/latest',
);

class ReleaseUpdate {
  const ReleaseUpdate._(this.version, this.build);

  static final _tagPattern = RegExp(r'^v(\d+\.\d+\.\d+)\+(\d+)$');

  final String version;
  final int build;

  static ReleaseUpdate? fromGitHub(Object? data, int installedBuild) {
    if (data is! Map || data['draft'] != false || data['prerelease'] != false) {
      return null;
    }
    final tag = data['tag_name'];
    final match = tag is String ? _tagPattern.firstMatch(tag) : null;
    final build = match == null ? null : int.tryParse(match[2]!);
    if (build == null || build <= installedBuild) return null;

    final assets = data['assets'];
    if (assets is! List ||
        !assets.whereType<Map>().any(
          (asset) =>
              asset['name'] == 'dpf-astra.apk' && asset['state'] == 'uploaded',
        )) {
      return null;
    }
    return ReleaseUpdate._(match![1]!, build);
  }

  Future<bool> open() async {
    try {
      return await _platform.invokeMethod<bool>(
            'openRelease',
            'v$version+$build',
          ) ??
          false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}

Future<ReleaseUpdate?> fetchUpdate() async {
  if (!Platform.isAndroid) return null;
  final client = HttpClient();
  try {
    final installedBuild = await _platform.invokeMethod<int>('installedBuild');
    if (installedBuild == null) return null;
    return await _fetchLatest(
      client,
      installedBuild,
    ).timeout(const Duration(seconds: 5));
  } on Exception {
    // An offline or unavailable update check must not interrupt monitoring.
    return null;
  } finally {
    client.close(force: true);
  }
}

Future<ReleaseUpdate?> _fetchLatest(
  HttpClient client,
  int installedBuild,
) async {
  final request = await client.getUrl(_latestRelease);
  request.headers.set(HttpHeaders.acceptHeader, 'application/vnd.github+json');
  final response = await request.close();
  if (response.statusCode != HttpStatus.ok) return null;
  final body = await response.transform(utf8.decoder).join();
  return ReleaseUpdate.fromGitHub(jsonDecode(body), installedBuild);
}
