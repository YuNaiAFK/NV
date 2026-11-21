import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';

void main() => runApp(const NvbookApp());

class Host {
  const Host();
  static const _channel = MethodChannel('nvbook/home');

  Future<Map<Object?, Object?>> loadSettings() async =>
      await _channel.invokeMapMethod<Object?, Object?>('settings') ?? {};

  Future<Map<Object?, Object?>> setTheme(String theme) async {
    final result = await _channel.invokeMapMethod<Object?, Object?>(
      'setTheme',
      {'theme': theme},
    );
    if (result == null) throw PlatformException(code: 'theme');
    return result;
  }

  Future<Map<Object?, Object?>> setCatalogPreference(
    String kind,
    String value,
  ) async {
    final result = await _channel.invokeMapMethod<Object?, Object?>(
      'setCatalogPreference',
      {'kind': kind, 'value': value},
    );
    if (result == null) throw PlatformException(code: 'catalog_preference');
    return result;
  }

  Future<CatalogPage> catalog(String query, int page) async {
    final response = await _channel.invokeMapMethod<Object?, Object?>(
      'catalog',
      {'query': query, 'page': page},
    );
    if (response == null) throw StateError('Missing catalog response');
    return CatalogPage(
      (response['rows'] as List)
          .map((row) => Cover.fromMap(row as Map))
          .toList(),
      response['pages'] as int,
    );
  }

  Future<void> open(String route, [int? id]) =>
      _channel.invokeMethod<void>('open', {'route': route, 'id': id});
}

class CatalogPage {
  const CatalogPage(this.rows, this.pages);
  final List<Cover> rows;
  final int pages;
}

class Cover {
  const Cover(this.id, this.title, this.thumbnail, this.flag, this.hidden);
  Cover.fromMap(Map row)
    : this(
        row['id'] as int,
        row['title'] as String,
        row['thumbnail'] as String,
        row['flag'] as String,
        row['hidden'] as bool,
      );
  final int id;
  final String title;
  final String thumbnail;
  final String flag;
  final bool hidden;
}

class NvbookApp extends StatefulWidget {
  const NvbookApp({super.key, this.host = const Host()});
  final Host host;

  @override
  State<NvbookApp> createState() => _NvbookAppState();
}

class _NvbookAppState extends State<NvbookApp> with WidgetsBindingObserver {
  Map<Object?, Object?> _settings = const {};
  var _startupFailed = false;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _reloadSettings();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _reloadSettings();
  }

  Future<void> _reloadSettings() async {
    final request = ++_request;
    try {
      final settings = await widget.host.loadSettings();
      if (mounted && request == _request) {
        setState(() {
          _settings = settings;
          _startupFailed = false;
        });
      }
    } on PlatformException {
      if (mounted && request == _request) setState(() => _startupFailed = true);
    }
  }

  Future<void> _changeTheme(String theme) async {
    final request = ++_request;
    final settings = await widget.host.setTheme(theme);
    if (mounted && request == _request) setState(() => _settings = settings);
  }

  Future<void> _changeCatalogPreference(String kind, String value) async {
    final request = ++_request;
    final settings = await widget.host.setCatalogPreference(kind, value);
    if (mounted && request == _request) setState(() => _settings = settings);
  }

  ThemeData _theme(Brightness brightness, {bool black = false}) {
    var colors = ColorScheme.fromSeed(
      seedColor: const Color(0xff66529f),
      brightness: brightness,
    );
    if (black) {
      colors = colors.copyWith(
        surface: Colors.black,
        surfaceContainer: const Color(0xff15131a),
        surfaceContainerLow: const Color(0xff101014),
      );
    }
    return ThemeData(
      useMaterial3: true,
      fontFamily: 'Roboto',
      colorScheme: colors,
      scaffoldBackgroundColor: colors.surface,
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: colors.surfaceContainer,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final appearance = _settings['theme'];
    return MaterialApp(
      title: 'NVbook',
      debugShowCheckedModeBanner: false,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark, black: appearance == 'black'),
      themeMode: switch (appearance) {
        'light' => ThemeMode.light,
        'dark' || 'black' => ThemeMode.dark,
        _ => ThemeMode.system,
      },
      home: _settings.isNotEmpty
          ? Workspace(
              host: widget.host,
              labels: _settings,
              onThemeChanged: _changeTheme,
              onCatalogPreferenceChanged: _changeCatalogPreference,
            )
          : Scaffold(
              body: Center(
                child: _startupFailed
                    ? FilledButton(
                        onPressed: _reloadSettings,
                        child: const Text('Retry'),
                      )
                    : const CircularProgressIndicator(),
              ),
            ),
    );
  }
}

class Workspace extends StatefulWidget {
  const Workspace({
    super.key,
    required this.host,
    required this.labels,
    required this.onThemeChanged,
    required this.onCatalogPreferenceChanged,
  });
  final Future<void> Function(String) onThemeChanged;
  final Future<void> Function(String, String) onCatalogPreferenceChanged;
  final Host host;
  final Map<Object?, Object?> labels;

  @override
  State<Workspace> createState() => _WorkspaceState();
}

class _WorkspaceState extends State<Workspace> {
  final _search = TextEditingController();
  final _scroll = ScrollController();
  List<Cover> _rows = [];
  String _query = '';
  int _tab = 0, _generation = 0, _page = 0, _lastPage = 1;
  bool _loading = false, _failed = false;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      if (!_failed && _scroll.position.extentAfter < 300) _nextPage();
    });
    _searchFor('');
  }

  @override
  void didUpdateWidget(covariant Workspace oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.labels['language'] != widget.labels['language'] ||
        oldWidget.labels['sort'] != widget.labels['sort']) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _searchFor(_query);
      });
    }
  }

  @override
  void dispose() {
    _search.dispose();
    _scroll.dispose();
    super.dispose();
  }

  String label(String key) => widget.labels[key] as String? ?? key;

  Future<void> _searchFor(String query) async {
    _generation++;
    setState(() {
      _rows = [];
      _query = query.trim();
      _page = 0;
      _lastPage = 1;
      _loading = false;
      _failed = false;
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
    await _nextPage();
  }

  Future<void> _nextPage() async {
    if (_loading || _page >= _lastPage) return;
    final generation = _generation;
    final next = _page + 1;
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      final result = await widget.host.catalog(_query, next);
      if (!mounted || generation != _generation) return;
      setState(() {
        final seen = _rows.map((row) => row.id).toSet();
        _rows.addAll(result.rows.where((row) => seen.add(row.id)));
        _lastPage = result.pages;
        _page = next;
      });
    } on PlatformException {
      if (mounted && generation == _generation) setState(() => _failed = true);
    } finally {
      if (mounted && generation == _generation)
        setState(() => _loading = false);
    }
  }

  Future<void> _open(String route, [int? id]) async {
    try {
      await widget.host.open(route, id);
    } on PlatformException {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(label('error'))));
    }
  }

  Widget _catalogPreference(String kind, String optionsKey, IconData icon) {
    final options = (widget.labels[optionsKey] as Map).cast<String, String>();
    final current = widget.labels[kind] as String?;
    final colors = Theme.of(context).colorScheme;
    return PopupMenuButton<String>(
      tooltip: label('${kind}Label'),
      initialValue: current,
      onSelected: (value) async {
        if (value == current) return;
        try {
          await widget.onCatalogPreferenceChanged(kind, value);
        } on PlatformException {
          if (mounted)
            ScaffoldMessenger.of(
              context,
            ).showSnackBar(SnackBar(content: Text(label('error'))));
        }
      },
      itemBuilder: (context) => [
        for (final option in options.entries)
          CheckedPopupMenuItem(
            value: option.key,
            checked: option.key == current,
            child: Text(option.value),
          ),
      ],
      child: Ink(
        decoration: BoxDecoration(
          color: colors.surfaceContainerLow,
          border: Border.all(color: colors.outlineVariant),
          borderRadius: BorderRadius.circular(22),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          child: Row(
            children: [
              Icon(icon, size: 17),
              const SizedBox(width: 7),
              Expanded(
                child: Text(
                  options[current] ?? label('${kind}Label'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const Icon(Icons.expand_more, size: 16),
            ],
          ),
        ),
      ),
    );
  }

  Widget _catalog() {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 6, 16, 14),
          child: SearchBar(
            controller: _search,
            elevation: const WidgetStatePropertyAll(0),
            side: WidgetStateProperty.resolveWith((states) {
              final colors = Theme.of(context).colorScheme;
              final focused = states.contains(WidgetState.focused);
              return BorderSide(
                color: focused ? colors.primary : colors.outlineVariant,
                width: focused ? 2 : 1,
              );
            }),
            leading: const Icon(Icons.search),
            hintText: label('search'),
            onSubmitted: _searchFor,
            trailing: [
              IconButton(
                tooltip: label('advanced'),
                icon: const Icon(Icons.tune_rounded),
                onPressed: () => _open('advanced'),
              ),
            ],
          ),
        ),
        if (widget.labels['languages'] is Map && widget.labels['sorts'] is Map)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Row(
              children: [
                Expanded(
                  child: _catalogPreference(
                    'language',
                    'languages',
                    Icons.language,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _catalogPreference('sort', 'sorts', Icons.sort),
                ),
              ],
            ),
          ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: () => _searchFor(_query),
            child: CustomScrollView(
              key: const PageStorageKey('catalog'),
              controller: _scroll,
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                if (_rows.isEmpty && !_loading)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: _failed ? _retry() : Text(label('empty')),
                    ),
                  ),
                if (_rows.isNotEmpty)
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
                    sliver: SliverGrid.builder(
                      itemCount: _rows.length,
                      gridDelegate:
                          const SliverGridDelegateWithMaxCrossAxisExtent(
                            maxCrossAxisExtent: 212,
                            childAspectRatio: .65,
                            crossAxisSpacing: 10,
                            mainAxisSpacing: 12,
                          ),
                      itemBuilder: (context, index) => _CoverTile(
                        cover: _rows[index],
                        showTitle: widget.labels['showTitles'] != false,
                        open: () => _open('gallery', _rows[index].id),
                      ),
                    ),
                  ),
                if (_loading || (_failed && _rows.isNotEmpty))
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Center(
                        child: _loading
                            ? const CircularProgressIndicator()
                            : _retry(),
                      ),
                    ),
                  ),
                if (_rows.isNotEmpty &&
                    !_failed &&
                    !_loading &&
                    _page < _lastPage)
                  SliverToBoxAdapter(
                    child: Center(
                      child: TextButton(
                        onPressed: _nextPage,
                        child: Text(label('more')),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _retry() =>
      FilledButton.tonal(onPressed: _nextPage, child: Text(label('retry')));

  Widget _destination(String key, IconData icon) => ListTile(
    contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 4),
    leading: Icon(icon),
    title: Text(label(key)),
    trailing: const Icon(Icons.chevron_right),
    onTap: key == 'appearance' ? _showThemePicker : () => _open(key),
  );

  Future<void> _showThemePicker() async {
    final selected = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(label('appearance')),
        children: [
          for (final mode in ['light', 'dark', 'black'])
            SimpleDialogOption(
              onPressed: () => Navigator.pop(context, mode),
              child: Row(
                children: [
                  Icon(
                    widget.labels['theme'] == mode
                        ? Icons.check_circle
                        : Icons.circle_outlined,
                  ),
                  const SizedBox(width: 12),
                  Text(label(mode)),
                ],
              ),
            ),
        ],
      ),
    );
    if (!mounted || selected == null || widget.labels['theme'] == selected)
      return;
    try {
      await widget.onThemeChanged(selected);
    } on PlatformException {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(label('error'))));
    }
  }

  Widget _panel(List<(String, IconData)> destinations) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
    children: [
      Card.filled(
        clipBehavior: Clip.antiAlias,
        child: Column(
          children: destinations
              .map((item) => _destination(item.$1, item.$2))
              .toList(),
        ),
      ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          _tab == 0 ? 'NVbook' : label(_tab == 1 ? 'library' : 'settings'),
          style: Theme.of(
            context,
          ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
        ),
        actions: _tab == 0
            ? [
                IconButton(
                  onPressed: () => _open('random'),
                  icon: const Icon(Icons.shuffle),
                  tooltip: label('random'),
                ),
              ]
            : null,
      ),
      body: IndexedStack(
        index: _tab,
        children: [
          _catalog(),
          _panel([
            ('favorites', Icons.favorite_outline),
            if (widget.labels['signedIn'] == true)
              ('online', Icons.cloud_outlined),
            ('downloads', Icons.download_outlined),
            ('bookmarks', Icons.bookmarks_outlined),
            ('history', Icons.history),
          ]),
          _panel([
            ('appearance', Icons.dark_mode_outlined),
            ('account', Icons.person_outline),
            ('settings', Icons.tune_outlined),
            ('tags', Icons.sell_outlined),
            ('status', Icons.label_outline),
            ('classic', Icons.view_list_outlined),
          ]),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (index) => setState(() => _tab = index),
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.explore_outlined),
            selectedIcon: const Icon(Icons.explore),
            label: label('browse'),
          ),
          NavigationDestination(
            icon: const Icon(Icons.collections_bookmark_outlined),
            selectedIcon: const Icon(Icons.collections_bookmark),
            label: label('library'),
          ),
          NavigationDestination(
            icon: const Icon(Icons.settings_outlined),
            selectedIcon: const Icon(Icons.settings),
            label: label('settings'),
          ),
        ],
      ),
    );
  }
}

class _CoverTile extends StatelessWidget {
  const _CoverTile({
    required this.cover,
    required this.showTitle,
    required this.open,
  });
  final Cover cover;
  final bool showTitle;
  final VoidCallback open;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    Widget placeholder(IconData icon) => ColoredBox(
      color: colors.surfaceContainer,
      child: Center(child: Icon(icon, color: colors.onSurfaceVariant)),
    );
    return Card.filled(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: open,
        child: Semantics(
          label: cover.title,
          button: true,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    if (cover.hidden)
                      placeholder(Icons.visibility_off_outlined)
                    else if (cover.thumbnail.isEmpty)
                      placeholder(Icons.image_outlined)
                    else
                      Image.network(
                        cover.thumbnail,
                        fit: BoxFit.cover,
                        cacheWidth: 420,
                        errorBuilder: (_, _, _) =>
                            placeholder(Icons.broken_image_outlined),
                      ),
                    if (cover.flag.isNotEmpty)
                      Positioned(
                        top: 7,
                        left: 7,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: colors.surface,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            child: Text(cover.flag),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              if (showTitle)
                Padding(
                  padding: const EdgeInsets.fromLTRB(10, 7, 10, 8),
                  child: Text(
                    cover.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
