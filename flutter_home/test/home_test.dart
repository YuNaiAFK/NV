import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nvbook_home/main.dart';

class FixtureHost extends Host {
  bool fail = false;
  String theme = 'light';
  String language = 'ALL';
  String sort = 'RECENT';
  final queries = <(String, int)>[];
  final routes = <(String, int?)>[];

  @override
  Future<Map<Object?, Object?>> loadSettings() async => {
    'theme': theme,
    'language': language,
    'sort': sort,
    'languages': {'ALL': 'All languages', 'JAPANESE': 'Japanese'},
    'sorts': {'RECENT': 'Recent', 'POPULAR': 'Popular'},
    'languageLabel': 'Gallery language',
    'sortLabel': 'Sort order',
    'appearance': 'Change theme',
    'light': 'Light',
    'dark': 'Dark',
    'black': 'True Black',
    'browse': 'Explore',
    'library': 'Library',
    'settings': 'Settings',
    'search': 'Search',
    'favorites': 'Favorites',
    'account': 'API key',
    'retry': 'Retry',
    'signedIn': true,
  };

  @override
  Future<Map<Object?, Object?>> setTheme(String mode) {
    theme = mode;
    return loadSettings();
  }

  @override
  Future<Map<Object?, Object?>> setCatalogPreference(
    String kind,
    String value,
  ) {
    if (kind == 'language') language = value;
    if (kind == 'sort') sort = value;
    return loadSettings();
  }

  @override
  Future<CatalogPage> catalog(String query, int page) async {
    queries.add((query, page));
    if (fail) throw PlatformException(code: 'offline');
    return CatalogPage([
      Cover(
        42,
        query.isEmpty ? 'Sample gallery' : 'Result for $query',
        '',
        '',
        false,
      ),
    ], 1);
  }

  @override
  Future<void> open(String route, [int? id]) async => routes.add((route, id));
}

void main() {
  testWidgets('language and sort return to page one without clearing search', (
    tester,
  ) async {
    final host = FixtureHost();
    await tester.pumpWidget(NvbookApp(host: host));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(EditableText), 'artbook');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    final before = host.queries.length;
    await tester.tap(find.byTooltip('Gallery language'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.widgetWithText(CheckedPopupMenuItem<String>, 'Japanese'),
    );
    await tester.pumpAndSettle();
    expect(host.language, 'JAPANESE');
    expect(find.text('Japanese'), findsOneWidget);
    await tester.tap(find.byTooltip('Sort order'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.widgetWithText(CheckedPopupMenuItem<String>, 'Popular'),
    );
    await tester.pumpAndSettle();
    expect(host.sort, 'POPULAR');
    expect(find.text('Popular'), findsOneWidget);
    expect(host.queries.skip(before).toList(), [
      ('artbook', 1),
      ('artbook', 1),
    ]);
  });
  testWidgets('theme picker applies dark and true black to Explore', (
    tester,
  ) async {
    final host = FixtureHost();
    await tester.pumpWidget(NvbookApp(host: host));
    await tester.pumpAndSettle();
    final context = tester.element(find.text('Sample gallery'));
    expect(Theme.of(context).brightness, Brightness.light);
    await tester.tap(find.widgetWithText(NavigationDestination, 'Settings'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change theme'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dark'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(NavigationDestination, 'Explore'));
    await tester.pumpAndSettle();
    expect(Theme.of(context).brightness, Brightness.dark);
    await tester.tap(find.widgetWithText(NavigationDestination, 'Settings'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change theme'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('True Black'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(NavigationDestination, 'Explore'));
    await tester.pumpAndSettle();
    expect(Theme.of(context).scaffoldBackgroundColor, Colors.black);
  });
  testWidgets('explore, search, open gallery, and access existing library', (
    tester,
  ) async {
    final host = FixtureHost();
    await tester.pumpWidget(NvbookApp(host: host));
    await tester.pumpAndSettle();
    expect(find.text('Sample gallery'), findsOneWidget);
    await tester.enterText(find.byType(EditableText), 'artbook');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(host.queries.last, ('artbook', 1));
    await tester.tap(find.text('Result for artbook'));
    expect(host.routes.last, ('gallery', 42));

    await tester.tap(find.widgetWithText(NavigationDestination, 'Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Favorites'));
    expect(host.routes.last, ('favorites', null));

    await tester.tap(find.widgetWithText(NavigationDestination, 'Settings'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('API key'));
    expect(host.routes.last, ('account', null));
  });

  testWidgets('retry recovers an unsuccessful catalog request', (tester) async {
    final host = FixtureHost()..fail = true;
    await tester.pumpWidget(NvbookApp(host: host));
    await tester.pumpAndSettle();
    expect(find.text('Retry'), findsOneWidget);
    host.fail = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Sample gallery'), findsOneWidget);
    expect(host.queries, [('', 1), ('', 1)]);
  });
}
