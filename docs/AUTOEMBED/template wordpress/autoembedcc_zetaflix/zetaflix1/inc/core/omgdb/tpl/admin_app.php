<div id="omegadb-omgdb-application" class="omegadb-app">
<div class="container">
	<header>
        <nav class="left" id="omegadb-types">
            <ul>
                <li>
					<h3 id="omgdb-logo-status">
						<a href="https://bescraper.cf" target="_blank"><img src="<?php echo get_template_directory_uri();?>/inc/core/omgdb/assets/logo.png"></a> 
						<span class="status-icon"></span>
						<small><?php echo OMEGADB_VERSION; ?></small>
					</h3></li>
            </ul>
            <div class="omgdb-cloud">
                <a class="dbdata consultor" href="#"><strong><?php _z('Credits'); ?>:</strong> <span id="omgdb-credits">0</span></a>
                <a class="dbdata consultor" href="#"><strong><?php _z('Used'); ?>:</strong> <span id="omgdb-credits-used">0</span></a>
                <a class="dbdata consultor" href="#"><strong><?php _z('Requests'); ?>:</strong> <span id="omgdb-requests">0</span></a>
            </div>
        </nav>
        <nav class="right" id="omegadb-settings">
            <ul>
                <li id="meta-updater-process">
					<div class="updater-wrap">
						<div class="progress-process">
							<div class="proccess-bar"><div class="into"></div></div>
							<span class="percentage"></span>
						</div>
						<div class="loading-spinner"><div class="spinner-ico"><div><div></div><div></div></div></div></div>
					</div>
                </li>
                <li id="meta-updater-controls">
					<div>
                    <a href="#" id="omgdb-finish-metaupdater"class="button button-secundary button-small metaupdater-control" data-control="finish" title="<?php _z('Stop');?>"><span class="dashicons dashicons-no-alt"></span><?php //_z('Finish'); ?></a>
                    <a href="#" class="button button-primary button-small metaupdater-control" data-control="continue" title="<?php _z('Continue');?>"><span class="dashicons dashicons-controls-play"></span><?php //_z('Continue'); ?></a>
					</div>
                </li>
                <li id="meta-updater-pause">
					<div>
                    <a href="#" class="button button-secundary button-small metaupdater-control" data-control="pause" title="<?php _z('Pause');?>"><i class="dashicons dashicons-controls-pause"></i></a>
					</div>
                </li>
                <li id="meta-updater-run">
                    <a href="#" id="omgdb-metaupdater" class="button button-primary button-small" data-control="updater" title="<?php _z('Start Meta Updater');?>"><?php _z('Meta Updater'); ?></a>
                </li>
            </ul>
			<input id="omgdb-metaupdater-nonce" type="hidden" value="<?php echo wp_create_nonce('omgdb-metaupdater-nonce'); ?>">
			<input id="omgdb-metaupdater-status" type="hidden" value="progress">
        </nav>
	</header>
    <!-- Header data -->
    <div class="omgdbapp">
        <!-- Type content Selector -->
        <nav class="left" id="omegadb-types">
            <ul>
                <li><a id="omgdbtabapp-movie" href="#" class="omgdb-tab-content button button-primary" data-type="movie"><?php _z('Movies'); ?></a></li>
                <li><a id="omgdbtabapp-tv" href="#" class="omgdb-tab-content button" data-type="tv"><?php _z('Shows'); ?></a></li>
            </ul>
        </nav>
		<nav class="right" id="omegadb-search">
			<form id="omegadb-form-search" class="right">
					<fieldset>
						<input type="text" id="omgdb-search-term" name="searchterm" placeholder="<?php _z('Search..'); ?>">
						<button type="submit" id="omgdb-btn-search" class="button button-large"><span class="search-ico"></span></button>
					</fieldset>
					<input type="hidden" id="omgdb-search-page" name="searchpage" value="1">
					<input type="hidden" id="omgdb-search-type" name="searchtype" value="movie">
					<input type="hidden" value="omegadb_app_search" name="action">
			</form>
		</nav>

    </div>
    <!-- Forms -->
    <div id="omgdb-forms-response" class="forms">
        <!-- Filter content for Year -->
        <form id="omegadb-form-filter">
            <fieldset class="collapse">
                <a class="button button-large omegadb-log-collapse" href="#" title="<?php _z('Collapse');?>"></a>
            </fieldset>
            <fieldset>
                <input type="number" id="omgdb-year" min="1900" max="<?php echo date('Y')+1; ?>" name="year" value="<?php echo rand('2000', date('Y')); ?>" placeholder="<?php _z('Year'); ?>">
            </fieldset>
            <fieldset>
                <input type="number" id="omgdb-page" min="1" name="page" value="1" placeholder="<?php _z('Page'); ?>">
            </fieldset>
            <fieldset>
                <select id="omgdb-popularity" name="popu">
                    <option value="popularity.desc"><?php _z('Popularity desc'); ?></option>
                    <option value="popularity.asc"><?php _z('Popularity asc'); ?></option>
                </select>
            </fieldset>
            <fieldset id="genres-box-movie" class="genres on">
                <select id="omgdb-movies-genres" name="genres-movie">
                    <?php echo $this->GenresMovies(); ?>
                </select>
            </fieldset>
            <fieldset id="genres-box-tv" class="genres">
                <select id="omgdb-tvshows-genres" name="genres-tv">
                    <?php echo $this->GenresTVShows(); ?>
                </select>
            </fieldset>
            <fieldset>
                <input type="submit" id="omgdb-btn-filter" class="button button-large" value="<?php _z('Discover'); ?>">
                <input type="hidden" value="omegadb_app_filter" name="action">
                <input type="hidden" id="omgdb-filter-type" name="type" value="movie">
            </fieldset>
            <fieldset id="bulk-importer-click">
                <a href="#" id="bulk-importer" class="button button-primary button-large"><?php _z('Bulk import'); ?></a>
            </fieldset>
			<div class="clearfix"></div>
        </form>
    </div>
    <!-- Progress Bar -->
    <div class="omgdb-progress-bar">
        <div class="progressing"></div>
    </div>
    <!-- Response Log -->
    <div class="omegadb-logs" style="display:none">
        <div id="omegadb-logs-box" class="box">
            <ul>
                <i id="omgdb-log-indicator"></i>
            </ul>
        </div>
        <div class="hidder">
            <a id="omegadb-cleanlog" href="#"><?php _z('Clean'); ?></a>
        </div>
    </div>
    <!-- Json Response -->
    <div class="wrapp">
        <div class="content">
            <input type="hidden" id="current-year">
            <input type="hidden" id="current-page">
            <input type="hidden" id="ztotal-items">
            <!-- Response results data -->
            <div id="omegadb-response-data"class="data_results">
                <section>
                    <?php echo sprintf(__z('About %s results (%s seconds)'),'<span id="omgdb-total-results">0</span>','<span id="time-execution-seconds">0</span>'); ?>
                </section>
                <section class="right">
                    <?php echo sprintf(__z('Loaded pages %s'),'<span id="omgdb-current-page">0</span>'); ?>
                </section>
            </div>
            <!-- Load results items -->
            <div id="omegadb-response-box" class="items">
                <i id="response-omegadb"></i>
            </div>
            <!-- Paginator -->
            <div class="paginator">
                <div id="omegadb-loadmore-spinner"></div>
                <a href="#" id="omegadb-loadmore" class="button button-primary omgdbloadmore"><?php _z('Load More'); ?></a>
            </div>
        </div>
    </div>
</div>
</div>
<!-- Go Top -->
<a id="omegadb-back-top" href="#" class="button button-secundary"><i class="dashicons dashicons-arrow-up-alt2"></i></a>
