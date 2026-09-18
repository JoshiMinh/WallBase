# WallBase Roadmap

* [ ] Database Module & ServiceLocator Removal: Create DatabaseModule providing Room DAOs, update repositories with @Inject constructors, migrate ViewModels to @HiltViewModel, and delete ServiceLocator.kt.
* [ ] Infinite Scrolling & Paging 3: Integrate AndroidX Paging 3 in WallpaperRepository for network sources (Reddit, Wallhaven, Pinterest) and collect paging data in Compose LazyVerticalGrid.
* [ ] UI Modernization & Dynamic Theming: Implement dynamic color palette extraction on DetailScreen, polish frosted glass chrome, and ensure accessibility compliance.
