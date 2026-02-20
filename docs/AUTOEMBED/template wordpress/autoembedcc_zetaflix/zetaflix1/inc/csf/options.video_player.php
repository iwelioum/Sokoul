<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Video Player'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'type'       => 'subheading',
                'content'    => __z('Video Player Settings')
            ),
			
            array(
                'id'    => 'splashscreen',
                'type'  => 'radio',
                'title' => __z('Default Splash'),
				'dependency' => array('watch_splash', '==', true),
                'options' => array(
                    'img' => __z('Splash Image'),
                    'fake' => __z('Fake Player'),
                ),
                'default' => 'img'
            ),
			
            array(
                'id'    => 'splashscreen_click',
                'type'  => 'radio',
                'title' => __z('Splash On-Click'),
				'dependency' => array('watch_splash', '==', true),
                'options' => array(
                    'item' => __z('Play first item on the player list'),
                    'vid' => __z('Play first actual video on the list'),
                ),
                'default' => 'vid'
            ),
            array(
                'id'    => 'playautoload',
                'type'  => 'switcher',
                'title' => __z('Auto Load'),
                'label' => __z('Load the first element of video player with the page'),
                'dependency' => array('playajax', '==', 'true')
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('The first element of the player will be loaded between 0 and 4 seconds after completing the total load of the page'),
                'dependency' => array('playajax|playautoload|watch_splash', '==|==|!=', 'true|true|true')
            ),
            array(
                'id'    => 'splashscreen_click',
                'type'  => 'radio',
                'title' => __z('Splash On-Click'),
				'dependency' => array('playautoload', '!=', 'true'),
                'options' => array(
                    'item' => __z('Load first item on the list'),
                    'vid' => __z('Load first actual video on the list'),
                ),
                'default' => 'vid'
            ),
			
            array(
                'id'      => 'playajax',
                'type'    => 'switcher',
                'title'   => __z('Ajax Mode'),
                'default' => true,
                'label'   => __z('This function delivers data safely and agile with WP-JSON')
            ),
            array(
                'id'    => 'nosplash',
                'type'  => 'radio',
                'title' => __z('Default Selected'),
				'dependency' => array('watch_splash|playajax', '!=|!=', 'true|true'),
                'options' => array(
                    'list' => __z('First item on player navigation'),
                    'vid' => __z('First real video on the player'),
					'trailerp' => __z('Trailer as player'),
					'fakep' => __z('Fake Player as player'),
                ),
                'default' => 'vid'
            ),
            array(
                'type'    => 'notice',
                'style'   => 'warning',
                'content' => __z('If there is no <strong>video</strong> available, "First item on player navigation" will be selected.'),
				'dependency' => array('watch_splash|nosplash|playajax', '!=|==|!=', 'true|vid|true'),
            ),
			
            array(
                'type'    => 'notice',
                'style'   => 'warning',
                'content' => __z('If there is no <strong>trailer video</strong> available, "First item on player navigation" will be selected.'),
				'dependency' => array('watch_splash|nosplash|playtrailer|playajax', '!=|==|==|!=', 'true|trailerp|true|true'),
            ),
			
            array(
                'type'    => 'submessage',
                'style'   => 'danger',
                'content' => __z('<strong>Trailer as Player must be enabled</strong>! If not, "First item on player navigation" will be used by default.'),
				'dependency' => array('watch_splash|nosplash|playtrailer|playajax', '!=|==|!=|!=', 'true|trailerp|true|true'),
            ),
            array(
                'type'    => 'submessage',
                'style'   => 'danger',
                'content' => __z('<strong>Fake Player as Player must be enabled</strong>! If not, "First item on player navigation" will be used by default.'),
				'dependency' => array('watch_splash|nosplash|playfake|playajax', '!=|==|!=|!=', 'true|fakep|true|true'),
            ),
            array(
                'id' => 'playajaxmethod',
                'type' => 'radio',
                'title' => __z('Delivery method'),
                'subtitle' => __z('Select the most convenient delivery method for your website.'),
                'default' => 'admin_ajax',
                'options' => array(
                    'admin_ajax' => '<code><strong>admin-ajax</strong></code> '.__z('This method is safe but not very agile'),
                    'wp_json'    => '<code><strong>wp-json</strong></code> '.__z('This method is simplified and very agile.')
                ),
                'dependency' => array('playajax', '==', true)
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('If you have important traffic it would be advisable not to activate this function, if it is activated we recommend deactivating the Auto Load'),
                'dependency' => array('playajax|playajaxmethod','==|==','true|admin_ajax')
            ),
            array(
                'type'    => 'notice',
                'style'   => 'info',
                'content' => __z('The selected delivery method is unsafe but very agile, if you have significant traffic we recommend disabling automatic loading'),
                'dependency' => array('playajax|playajaxmethod','==|==','true|wp_json')
            ),
            array(
                'id'    => 'playwait',
                'type'  => 'text',
                'title' => __z('Timeout'),
                'subtitle'  => __z('Time to wait in seconds before displaying Video Player'),
                'default' => '10',
                'attributes' => array(
                    'style' => 'width:100px',
                    'type' => 'number'
                ),
                'dependency' => array('playajax', '==', true)
            ),
            array(
                'id'    => 'playauto',
                'type'  => 'checkbox',
                'title' => __z('Auto Play'),
                'subtitle'  => __z('Check if you want the videos to play automatically'),
                'options' => array(
                    'ytb' => __z('Auto-play YouTube videos'),
                    'jwp' => __z('Auto-play JWPlayer videos')
                )
            ),
            array(
                'id'    => 'playsize',
                'type'  => 'radio',
                'title' => __z('Player size'),
                'subtitle'  => __z('Select a specific size for video player'),
                'options' => array(
                    'regular' => __z('Regular size'),
                    'bigger'  => __z('Bigger size'),
                ),
                'default' => 'regular'
            ),
            array(
                'id'      => 'jwpage',
                'type'    => 'select',
                'title'   => __z('Page jwplayer'),
                'subtitle'    => __z('Select page to display player'),
                'options' => 'pages'
            ),
            array(
                'id'    => 'player',
                'type'  => 'radio',
                'title' => __z('Player'),
                'options' => array(
                    'jwp8' => __z('JW Player 8'),
                    'jwp7' => __z('JW Player 7'),
                    'plyr' => __z('Plyr.io')
                ),
                'default' => 'plyr'
            ),
            array(
                'id'      => 'playercolor',
                'type'    => 'color',
                'title'   => __z('Main color'),
                'subtitle'    => __z('Choose a color'),
                'default' => '#d40b12'
            ),
            array(
                'id'      => 'jwkey',
                'type'    => 'text',
                'title'   => __z('License Key'),
                'subtitle'    => __z('JW Player 7 (Self-Hosted)'),
                'default' => 'IMtAJf5X9E17C1gol8B45QJL5vWOCxYUDyznpA==',
                'dependency' => array('player', '==', 'jwp7')
            ),
            array(
                'id'      => 'jw8key',
                'type'    => 'text',
                'title'   => __z('License Key'),
                'subtitle'    => __z('JW Player 8 (Self-Hosted)'),
                'default' => '64HPbvSQorQcd52B8XFuhMtEoitbvY/EXJmMBfKcXZQU2Rnn',
                'dependency' => array('player', '==', 'jwp8')
            ),
            array(
                'id'      => 'jwabout',
                'type'    => 'text',
                'title'   => __z('About text'),
                'subtitle'    => __z('JW Player About text in right click'),
                'default' => 'Powered by JW Player',
                'dependency' => array('player', '!=', 'plyr')
            ),
            array(
                'id'    => 'jwlogo',
                'type'  => 'media',
                'title' => __z('Logo player'),
                'subtitle'  => __z('Upload your logo using the Upload Button or insert image URL'),
                'dependency' => array('player', '!=', 'plyr')
            ),
            array(
                'id'    => 'jwposition',
                'type'  => 'select',
                'title' => __z('Logo position'),
                'subtitle'  => __z('Select a postion for logo player'),
                'options' => array(
                    'top-left'     => __z('Top left'),
                    'top-right'    => __z('Top right'),
                    'bottom-left'  => __z('Bottom left'),
                    'bottom-right' => __z('Bottom right')
                ),
                'dependency' => array('player', '!=', 'plyr')
            ),
            array(
                'type'       => 'subheading',
                'content'    => __z('Extra Players')
            ),
			array(
			  'type'    => 'submessage',
			  'style'   => 'warning',
			  'content' => 'These players only appear on movies oage, it does <strong>not</strong> show on tvshows, seasons and episodes.',
			),
            array(
                'id'      => 'playtrailer',
                'type'    => 'switcher',
                'title'   => __z('Trailer as Player'),
                'label'   => __z('Add trailer on list of player option.'),
				'default' => true
            ),
            array(
                'id'    => 'trailertitle',
                'type'  => 'text',
                'title' => __z('Title'),
                'placeholder' => 'Trailer',
                'dependency' => array('playtrailer', '==', true)
            ),
            array(
                'id'    => 'trailersource',
                'type'  => 'text',
                'title' => __z('Source Name'),
                'placeholder' => 'Youtube',
                'dependency' => array('playtrailer', '==', true)
            ),
            array(
                'id'    => 'trailerposition',
                'type'  => 'radio',
                'title' => __z('Position'),
                'options' => array(
                    'first' => __z('Before first video'),
                    'last' => __z('After last video')
                ),
                'dependency' => array('playtrailer', '==', true),
                'default' => 'first'
            ),
           array(
                'id'      => 'playfake',
                'type'    => 'switcher',
                'title'   => __z('Fake Player as Player'),
                'label'   => __z('Add trailer on list of player option.'),
				'default' => false
            ),
            array(
                'id'    => 'faketitle',
                'type'  => 'text',
                'title' => __z('Title'),
                'placeholder' => __z('Premium'),
                'dependency' => array('playfake', '==', true)
            ),
            array(
                'id'    => 'fakesource',
                'type'  => 'text',
                'title' => __z('Source Name'),
                'placeholder' => __z('HD Server'),
                'dependency' => array('playfake', '==', true)
            ),
            array(
                'id'    => 'fakeposition',
                'type'  => 'radio',
                'title' => __z(' Position'),
                'options' => array(
                    'first' => __z('First on list'),
                    'last' => __z('Last on list')
                ),
                'dependency' => array('playfake', '==', true),
                'default' => 'first'
            ),
            array(
                'type'       => 'subheading',
                'content'    => __z('Fake Player Settings')
            ),
            array(
                'id'       => 'fakeoptions',
                'type'     => 'checkbox',
                'title'    => __z('Show in Fake Player'),
                'options'  => array(
                    'pla' => __z('Play button'),
                    'ads' => __z('Ad Label'),
                    'qua' => __z('HD Quality')
                ),
                'default'  => array('pla','ads','qua'),
            ),
            array(
                'type'       => 'content',
                'content'    => '<h2>'.__z('Advertising links for fake player').'</h2>',
            ),
            array(
                'type'       => 'content',
                'content'    => '<p style="margin-bottom: 0">'.__z('Add as many ad links as you wish, these are displayed randomly in the Fake Player').'</p>',
            ),
            array(
                'id'              => 'fakeplayerlinks',
                'type'            => 'group',
                'button_title'    => __z('Add link'),
                'accordion_title' => __z('Add new link'),
                'fields' => array(
                    array(
                        'id'   => 'link',
                        'type' => 'text',
                        'attributes' => array(
                            'placeholder' => 'http://'
                        )
                    )
                )
            ),
        )
    )
);
