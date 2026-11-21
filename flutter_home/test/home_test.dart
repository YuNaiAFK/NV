import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nvbook_home/main.dart';

class FixtureHost extends Host {
  bool fail = false;
  final queries = <(String, int)>[];
  final routes = <(String, int?)>[];

  @override
  Future<Map<Object?, Object?>> loadSettings() async => {
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
