<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'id'    => 'settings',
        'icon'  => 'fa fa-cog',
        'title' => __z('Settings')
    )
);

/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Main settings'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'online',
                'type'    => 'switcher',
                'title'   => __z('Site Online'),
                'label'   => __z('Keep this field activated'),
                'default' => true
            ),
            array(
                'type'       => 'notice',
                'style'      => 'warning',
                'content'    => __z('Currently your website is in <strong>offline mode</strong>'),
                'dependency' => array('online', '!=', true)
            ),
            array(
                'id'      => 'offlinemessage',
                'type'    => 'textarea',
                'title'   => __z('Offline Message'),
                'default' => __z('We are in maintenance, please try it later'),
                'attributes' => array(
                    'placeholder' => __z('Offline mode message here'),
                    'rows'        => 3,
                ),
                'dependency' => array('online', '!=', true)
            ),
            array(
                'id'      => 'classic_editor',
                'type'    => 'switcher',
                'title'   => __z('Classic Editor'),
                'label'   => __z('Enable classic editor in content editors')
            ),
            array(
                'type'       => 'notice',
                'style'      => 'info',
                'content'    => __z('WordPress Gutenberg editor has been disabled'),
                'dependency' => array('classic_editor', '==', true)
            ),
			array(
				'type' => 'subheading',
				'content' => __z('Homepage')
			),
            array(
                'id'    => 'homepage',
                'type'  => 'sorter',
                'default' => array(
                    'enabled' => array(                      
                        'movies'        => __z('Movies'),
						'tvshows'        => __z('TV Shows'),
						'top-imdb'        => __z('TOP IMDb'),
						'popular'        => __z('Trending'), 
						
                    ),
                    'disabled' => array(
							'genreswidget' => __z('Genres Widget'),
							'seasons'        => __z('TV Shows > Seasons'),
							'episodes'        => __z('TV Shows > Episodes'),
							'ads'           => __z('Advertisement 1'),
							'ads-2'           => __z('Advertisement 2'),
							'ads-3'           => __z('Advertisement 3'),
                    ),
                ),
                'enabled_title'  => __z('Modules enabled'),
                'disabled_title' => __z('Modules disabled'),
            ),
            array(
                'id'    => 'ganalytics',
                'type'  => 'text',
                'title' => __z('Google Analytics'),
                'subtitle'  => __z('Insert tracking code to use this function'),
                'attributes' => array(
                    'placeholder' => 'UA-45182606-12',
                    'style' => 'width:200px'
                )
            ),
            array(
                'id'      => 'iperpage',
                'type'    => 'text',
                'title'   => __z('Items per page'),
                'subtitle'    => __z('Archive pages show at most'),
                'default' => '30',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number'
                )
            ),
            array(
                'id'      => 'bperpage',
                'type'    => 'text',
                'title'   => __z('Post per page in blog'),
                'subtitle'    => __z('Archive pages show at most'),
                'default' => '10',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number'
                )
            ),
            array(
                'id'      => 'itopimdb',
                'type'    => 'text',
                'title'   => __z('TOP IMDb items'),
                'subtitle'    => __z('Select the number of items to the page TOP IMDb'),
                'default' => '50',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number'
                )
            ),
            array(
                'id'    => 'pagrange',
                'type'  => 'text',
                'title' => __z('Pagination Range'),
                'subtitle'  => __z('Set a range of items to display in the paginator'),
                'default' => '2',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number',
                    'max' => 4,
                    'min' => 1
                )
            ),
            array(
                'id'    => 'permits',
                'type'  => 'checkbox',
                'title' => __z('General'),
                'subtitle'  => __z('Check whether to activate or deactivate'),
                'options' => array(
                    'sab'  => __z('Show admin bar'),
                    'eusr' => __z('User register enable'),
                    'enls' => __z('Live search enable'),
                    'socl' => __z('Share Buttons'),
                    'trlr' => __z('Watch Trailer'),
                    'demj' => __z('Emoji disable'),
                    'mhtm' => __z('Minify HTML'),
                ),
                'default' => array('enls','esst','demj','mhtm','slgl')
            ),
			

            array(
                'id' => 'view_count_mode',
                'type' => 'radio',
                'title' => __z('View count'),
                'subtitle' => __z('Methods for counting views in content'),
                'default' => 'regular',
                'options' => array(
                    'regular' => __z('Regular'),
                    'ajax'    => __z('Ajax'),
                    'none'    => __z('Disable view counting')
                )
            ),
            array(
                'type'       => 'notice',
                'style'      => 'info',
                'content'    => __z('Regular view count may consume resources from your server in a moderate way, consider disabling it if your server has limited processes.'),
                'dependency' => array('view_count_mode', '==', 'regular')
            ),
            array(
                'type'       => 'notice',
                'style'      => 'warning',
                'content'    => __z('View count by Ajax consumes resources from your server on each user visit, if your server has limited processes we recommend disabling this function.'),
                'dependency' => array('view_count_mode', '==', 'ajax')
            ),

            array(
                'type' => 'subheading',
                'content' => __z('reCaptcha')
            ),
			/*
            array(
                'id' => 'sitesecurity',
                'type' => 'radio',
                'title' => __z('Anti-Spam API'),
                'subtitle' => __z('Select desired security api'),
                'default' => 'regular',
                'options' => array(
                    'hcaptcha' => __z('hCaptcha'),
                    'recaptcha'    => __z('reCaptcha'),
                )
            ),
			*/
			/*
            array(
                'id'      => 'hcaptchasecret',
                'type'    => 'text',
                'title'   => __z('Secret key'),
				'subtitle' => '<a href="https://dashboard.hcaptcha.com/settings" target="_blank">'.__z('Get hCaptcha secret key').'</a>',
				'dependency' => array('sitesecurity','==', 'hcaptcha')
            ),
			
            array(
                'id'      => 'gcaptchasitekey',
                'type'    => 'text',
                'title'   => __z('Site key'),
				'subtitle' => '<a href="https://dashboard.hcaptcha.com/sites" target="_blank">'.__z('Get hCaptcha site key').'</a>',
				'dependency' => array('sitesecurity','==', 'hcaptcha')
            ),
			*/
            array(
                'id'      => 'gcaptchasitekeyv3',
                'type'    => 'text',
                'title'   => __z('Site key'),
				/*'dependency' => array('sitesecurity','==', 'recaptcha')*/
            ),
            array(
                'id'      => 'gcaptchasecretv3',
                'type'    => 'text',
                'title'   => __z('Secret key'),
				/*'dependency' => array('sitesecurity','==', 'recaptcha')*/
            ),
            array(
                'type' => 'content',
                'content' => '<a href="https://www.google.com/recaptcha/admin" target="_blank">'.__z('Get Google reCAPTCHA').'</a>',
				/*'dependency' => array('sitesecurity','==', 'recaptcha')*/
            ),
            array(
                'type' => 'subheading',
                'content' => __z('Database cache')
            ),
            array(
                'id'      => 'cachescheduler',
                'type'    => 'radio',
                'title'   => __z('Scheduler'),
                'subtitle'    => __z('Cache cleaning'),
                'before'   => '<p>'.__z('It is important to clean expired cache at least once a day').'</p>',
                'default' => 'daily',
                'options' => array(
                    'daily'      => __z('Daily'),
                    'twicedaily' => __z('Twice daily'),
                    'hourly'     => __z('Hourly')
                ),
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('Storing cache as long as possible can be a very good idea'),
                'dependency' => array('cachetime', '<=', 43200)
            ),
            array(
                'id'      => 'cachetime',
                'type'    => 'text',
                'title'   => __z('Cache Timeout'),
                'subtitle'    => __z('Set the time in seconds'),
                'default' => '86400',
                'before'   => '<p>'.__z('We recommend storing this cache for at least 86400 seconds').'</p>',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number'
                )
            )
        )
    )
);
