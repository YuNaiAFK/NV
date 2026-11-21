import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nvbook_home/main.dart';

class LayoutHost extends Host {
  @override
  Future<Map<Object?, Object?>> loadSettings() async => {
    'browse': 'Explore',
    'library': 'Library',
    'settings': 'Settings',
    'search': 'Search galleries',
    'advanced': 'Search filters',
    'language': 'ALL',
    'sort': 'RECENT',
    'languages': {'ALL': 'All languages', 'JAPANESE': 'Japanese'},
    'sorts': {'RECENT': 'Recent', 'POPULAR': 'Popular this week'},
    'languageLabel': 'Gallery language',
    'sortLabel': 'Sort order',
    'favorites': 'Local favorites',
    'online': 'Website favorites',
    'downloads': 'Downloads',
    'bookmarks': 'Bookmarks',
    'history': 'Reading history',
    'signedIn': true,
  };

  @override
  Future<CatalogPage> catalog(String query, int page) async =>
      const CatalogPage([
        Cover(1, 'Contemporary Illustration', '', 'JP', false),
        Cover(2, 'The Art of Everyday Objects', '', 'EN', false),
        Cover(3, 'A Field Guide to Clouds', '', 'EN', false),
        Cover(4, 'Notes from the Coast', '', '', true),
      ], 1);
}

Future<void> capture(WidgetTester tester, Key key, String name) async {
  final directory = Platform.environment['NVBOOK_VISUAL_DIR'];
  if (directory == null) return;
  final boundary = tester.renderObject<RenderRepaintBoundary>(find.byKey(key));
  final image = await boundary.toImage(pixelRatio: 1);
  final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!;
  Directory(directory).createSync(recursive: true);
  File('$directory/$name.png').writeAsBytesSync(bytes.buffer.asUint8List());
  image.dispose();
}

Future<void> loadFixtureFonts() async {
  final directory = Platform.environment['NVBOOK_FIXTURE_FONT_DIR'];
  if (directory == null) return;
  final text = FontLoader('Roboto');
  for (final name in [
    'roboto-regular.ttf',
    'roboto-medium.ttf',
    'roboto-bold.ttf',
  ]) {
    text.addFont(
      Future.value(
        ByteData.sublistView(File('$directory/$name').readAsBytesSync()),
      ),
    );
  }
  await text.load();
  final icon = Icons.search;
  final family = icon.fontPackage == null
      ? icon.fontFamily!
      : 'packages/${icon.fontPackage}/${icon.fontFamily}';
  final shapes = FontLoader(family);
  shapes.addFont(
    Future.value(
      ByteData.sublistView(
        File('$directory/materialicons-regular.otf').readAsBytesSync(),
      ),
    ),
  );
  await shapes.load();
}

void main() {
  testWidgets('phone layout keeps catalog and library reachable', (
    tester,
  ) async {
    await tester.runAsync(loadFixtureFonts);
    const surface = Key('visual');
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      RepaintBoundary(
        key: surface,
        child: NvbookApp(host: LayoutHost()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Contemporary Illustration'), findsOneWidget);
    expect(find.byIcon(Icons.visibility_off_outlined), findsOneWidget);
    expect(find.byTooltip('Gallery language'), findsOneWidget);
    expect(find.byTooltip('Sort order'), findsOneWidget);
    await tester.runAsync(() => capture(tester, surface, 'explore'));
    await tester.tap(find.widgetWithText(NavigationDestination, 'Library'));
    await tester.pumpAndSettle();
    expect(find.text('Local favorites'), findsOneWidget);
    await tester.runAsync(() => capture(tester, surface, 'library'));
    expect(tester.takeException(), isNull);
  });
}
