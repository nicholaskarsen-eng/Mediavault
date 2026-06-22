import React, { useState, useMemo } from 'react';

// Sample visual mock assets with placeholders to satisfy responsive layout requirements
const INITIAL_MEDIA_ITEMS = [
  {
    id: 1,
    title: 'Sunset over Sierra mountains',
    type: 'IMAGE',
    category: 'Nature',
    aspectRatio: 'aspect-[16/10]',
    size: '4.2 MB',
    date: '2026-06-22',
    gradient: 'from-amber-500 to-rose-600',
    tags: ['landscape', 'sunset', 'scenic']
  },
  {
    id: 2,
    title: 'Weekly Sync Board Session',
    type: 'IMAGE',
    category: 'Work',
    aspectRatio: 'aspect-[4/3]',
    size: '1.8 MB',
    date: '2026-06-21',
    gradient: 'from-blue-600 to-indigo-700',
    tags: ['agile', 'kanban', 'collaboration']
  },
  {
    id: 3,
    title: 'Laughing kitten plays in boxes',
    type: 'VIDEO',
    category: 'Memes',
    aspectRatio: 'aspect-video',
    duration: '0:15',
    size: '12.4 MB',
    date: '2026-06-22',
    gradient: 'from-yellow-400 to-orange-500',
    tags: ['cute', 'cat', 'viral']
  },
  {
    id: 4,
    title: 'Quarter 2 Financial Statement Analysis',
    type: 'DOCUMENT',
    category: 'Finance',
    aspectRatio: 'aspect-[3/4]',
    size: '830 KB',
    date: '2026-06-18',
    gradient: 'from-emerald-500 to-teal-600',
    tags: ['invoice', 'pdf', 'planning']
  },
  {
    id: 5,
    title: 'Secure blockchain ledgers schematic',
    type: 'IMAGE',
    category: 'Finance',
    aspectRatio: 'aspect-square',
    size: '2.5 MB',
    date: '2026-06-15',
    gradient: 'from-cyan-500 to-blue-600',
    tags: ['crypto', 'diagram', 'ledger']
  },
  {
    id: 6,
    title: 'Synth Wave background loops',
    type: 'VIDEO',
    category: 'Personal',
    aspectRatio: 'aspect-video',
    duration: '3:40',
    size: '48.1 MB',
    date: '2026-06-12',
    gradient: 'from-purple-600 to-pink-600',
    tags: ['retro', 'music', 'visualizer']
  },
  {
    id: 7,
    title: 'Developer terminal configuration screenshot',
    type: 'IMAGE',
    category: 'Work',
    aspectRatio: 'aspect-[16/10]',
    size: '1.1 MB',
    date: '2026-06-20',
    gradient: 'from-slate-700 to-slate-900',
    tags: ['cli', 'workspace', 'linux']
  },
  {
    id: 8,
    title: 'Funny Zoom glitch compilations',
    type: 'VIDEO',
    category: 'Memes',
    aspectRatio: 'aspect-video',
    duration: '1:05',
    size: '22.0 MB',
    date: '2026-06-19',
    gradient: 'from-rose-500 to-pink-500',
    tags: ['comedy', 'remote', 'work-from-home']
  }
];

const CATEGORIES = ['All', 'Nature', 'Work', 'Memes', 'Finance', 'Personal'];

export default function MediaGallery() {
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [viewMode, setViewMode] = useState('grid'); // 'grid' | 'masonry' | 'compact'
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedItem, setSelectedItem] = useState(null);

  // Filter implementation
  const filteredItems = useMemo(() => {
    return INITIAL_MEDIA_ITEMS.filter((item) => {
      const matchCategory = selectedCategory === 'All' || item.category === selectedCategory;
      const matchQuery = searchQuery === '' || 
        item.title.toLowerCase().includes(searchQuery.toLowerCase()) || 
        item.tags.some(t => t.toLowerCase().includes(searchQuery.toLowerCase()));
      return matchCategory && matchQuery;
    });
  }, [selectedCategory, searchQuery]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10 font-sans">
      {/* Container Wrapper */}
      <div className="max-w-7xl mx-auto space-y-8">
        
        {/* Header Area */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800 pb-6">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-blue-400 to-indigo-500 bg-clip-text text-transparent">
              OmniVault Media Grid
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Fully responsive client-side visual repo with reactive category filters
            </p>
          </div>

          {/* Search Box */}
          <div className="relative w-full md:w-80">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-slate-500">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </span>
            <input
              type="text"
              placeholder="Search title or tag..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-slate-900 border border-slate-800 rounded-xl py-2 pl-10 pr-4 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all placeholder:text-slate-500"
            />
          </div>
        </div>

        {/* Filter Toolbar controls */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          
          {/* Category Chips */}
          <div className="flex flex-wrap gap-2">
            {CATEGORIES.map((cat) => {
              const active = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-4 py-1.5 rounded-full text-xs font-semibold tracking-wide transition-all ${
                    active
                      ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/20 pointer-events-none'
                      : 'bg-slate-900 text-slate-300 border border-slate-800 hover:bg-slate-800 hover:text-white'
                  }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>

          {/* Layout Grid Mode toggles */}
          <div className="flex items-center gap-1 bg-slate-900 border border-slate-800 rounded-lg p-1 self-start">
            <button
              onClick={() => setViewMode('grid')}
              className={`p-1.5 rounded-md transition-all ${viewMode === 'grid' ? 'bg-slate-800 text-blue-400' : 'text-slate-500 hover:text-slate-300'}`}
              title="Standard Grid"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
              </svg>
            </button>
            <button
              onClick={() => setViewMode('masonry')}
              className={`p-1.5 rounded-md transition-all ${viewMode === 'masonry' ? 'bg-slate-800 text-blue-400' : 'text-slate-500 hover:text-slate-300'}`}
              title="Masonry Variations"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </button>
            <button
              onClick={() => setViewMode('compact')}
              className={`p-1.5 rounded-md transition-all ${viewMode === 'compact' ? 'bg-slate-800 text-blue-400' : 'text-slate-500 hover:text-slate-300'}`}
              title="Compact View"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
          </div>
        </div>

        {/* Media Placeholder Gallery Grid */}
        {filteredItems.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 border border-dashed border-slate-800 rounded-3xl bg-slate-900/20 backdrop-blur-sm">
            <svg className="w-12 h-12 text-slate-600 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <h3 className="text-lg font-bold text-slate-300">No media assets found</h3>
            <p className="text-xs text-slate-500 mt-1 max-w-xs text-center">
              Try readjusting your custom filters or clear your search input above.
            </p>
          </div>
        ) : (
          <div
            className={`grid gap-6 transition-all duration-300 ${
              viewMode === 'compact'
                ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3'
                : viewMode === 'masonry'
                ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 items-start'
                : 'grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4'
            }`}
          >
            {filteredItems.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelectedItem(item)}
                className={`group relative overflow-hidden bg-slate-900 border border-slate-800 rounded-2xl cursor-pointer hover:border-slate-700 hover:shadow-xl hover:shadow-blue-500/5 transition-all duration-300 ${
                  viewMode === 'compact' ? 'flex items-center gap-4 p-3' : 'flex flex-col'
                }`}
              >
                {/* Visual Placeholder Block representing images/videos */}
                <div
                  className={`relative overflow-hidden w-full bg-gradient-to-br ${item.gradient} flex-shrink-0 transition-transform duration-500 group-hover:scale-[1.02] ${
                    viewMode === 'compact' ? 'w-20 h-20 rounded-xl' : `${item.aspectRatio} rounded-t-2xl`
                  }`}
                >
                  {/* Absolute subtle background pattern overlay */}
                  <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-white/10 to-transparent mix-blend-overlay" />
                  
                  {/* Decorative modern graphic grid placeholder lines */}
                  <div className="absolute inset-0 flex items-center justify-center opacity-25">
                    <div className="w-11/12 h-5/6 border border-white/20 rounded-md flex items-center justify-center">
                      <div className="w-5/6 h-4/6 border border-dashed border-white/10 rounded flex items-center justify-center">
                        <span className="text-white text-[10px] font-mono select-none">{item.type}</span>
                      </div>
                    </div>
                  </div>

                  {/* Indicator icons for Video or Image types */}
                  {item.type === 'VIDEO' && (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <div className="w-12 h-12 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center border border-white/20 shadow-lg group-hover:bg-blue-600/90 group-hover:border-transparent transition-all duration-300">
                        <svg className="w-5 h-5 text-white fill-current ml-0.5" viewBox="0 0 24 24">
                          <path d="M8 5v14l11-7z" />
                        </svg>
                      </div>
                    </div>
                  )}

                  {/* Metadata overlays for non-compact layouts */}
                  {viewMode !== 'compact' && (
                    <>
                      <div className="absolute top-3 left-3 bg-slate-950/75 backdrop-blur-md border border-slate-800/85 px-2.5 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider text-slate-300">
                        {item.category}
                      </div>

                      <div className="absolute bottom-3 right-3 bg-slate-950/75 backdrop-blur-md px-2 py-0.5 rounded text-[10px] text-slate-300 font-mono">
                        {item.type === 'VIDEO' ? `Video • ${item.duration}` : 'Image'}
                      </div>
                    </>
                  )}
                </div>

                {/* Card Content Info section */}
                <div className={`p-4 flex-1 flex flex-col justify-between ${viewMode === 'compact' ? '!p-0' : ''}`}>
                  <div className="space-y-1.5">
                    <h4 className="font-semibold text-slate-200 group-hover:text-white line-clamp-1 text-sm tracking-wide transition-colors">
                      {item.title}
                    </h4>

                    {/* Tag list rendering */}
                    {viewMode !== 'compact' && (
                      <div className="flex flex-wrap gap-1.5 pt-1">
                        {item.tags.map((tag) => (
                          <span
                            key={tag}
                            className="bg-slate-950 px-2 py-0.5 rounded-md text-[10px] font-medium text-slate-400 border border-slate-800"
                          >
                            #{tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Visual Sizing & Sync status metrics */}
                  <div className={`flex items-center justify-between mt-4 pb-0.5 border-t border-slate-850 pt-2 ${viewMode === 'compact' ? 'hidden' : ''}`}>
                    <span className="text-[11px] text-slate-500 font-mono">{item.size}</span>
                    <span className="text-[11px] text-slate-500 font-mono">{item.date}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Full Details Modal Backdrop */}
        {selectedItem && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md transition-all animate-fade-in">
            <div className="relative w-full max-w-xl bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden p-6 space-y-6">
              
              {/* Close action */}
              <button
                onClick={() => setSelectedItem(null)}
                className="absolute top-4 right-4 text-slate-400 hover:text-white transition-colors"
                title="Dismiss Dialog"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>

              {/* Banner visual zoom representational box */}
              <div className={`w-full ${selectedItem.aspectRatio} rounded-xl bg-gradient-to-br ${selectedItem.gradient} flex items-center justify-center relative overflow-hidden shadow-inner`}>
                <div className="absolute inset-0 bg-black/10 backdrop-brightness-95" />
                <div className="relative border border-white/20 p-8 rounded-lg border-dashed text-center">
                  <span className="text-white text-xs font-mono uppercase tracking-widest">{selectedItem.type} PLAYBACK RENDERER</span>
                  <p className="text-[10px] text-white/60 mt-1">Simulated responsive preview canvas</p>
                </div>
              </div>

              {/* Text explanations info */}
              <div className="space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h3 className="text-lg font-bold text-white">{selectedItem.title}</h3>
                    <p className="text-xs text-blue-400 mt-0.5">Category: {selectedItem.category}</p>
                  </div>
                  <span className="bg-slate-950 border border-slate-800 text-[11px] text-slate-300 font-mono px-3 py-1 rounded-full">
                    {selectedItem.size}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-4 border-y border-slate-850 py-3 text-xs font-mono text-slate-400">
                  <div>Registered: {selectedItem.date}</div>
                  <div>Type: {selectedItem.type}</div>
                </div>

                <div className="space-y-2">
                  <h5 className="text-xs font-semibold text-slate-300 uppercase tracking-wider">Indexed Tag Handles</h5>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedItem.tags.map(tag => (
                      <span key={tag} className="bg-slate-950 px-2.5 py-1 rounded-md text-xs text-slate-400 border border-slate-800">
                        #{tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {/* Action layout */}
              <div className="flex justify-end gap-3 pt-2">
                <button
                  onClick={() => setSelectedItem(null)}
                  className="px-4 py-2 bg-slate-950 hover:bg-slate-850 hover:text-white rounded-xl text-xs font-semibold transition-colors border border-slate-800"
                >
                  Dismiss
                </button>
                <button
                  onClick={() => {
                    alert(`Simulating secure local retrieval for: ${selectedItem.title}`);
                    setSelectedItem(null);
                  }}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold transition-all shadow-lg hover:shadow-blue-500/25"
                >
                  Retrieve Element
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
